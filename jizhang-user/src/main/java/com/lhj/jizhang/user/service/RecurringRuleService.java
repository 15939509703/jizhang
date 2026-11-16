package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.BookMenu;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.RecurringExecutionOutDTO;
import com.lhj.jizhang.user.dto.RecurringRuleCreateInDTO;
import com.lhj.jizhang.user.dto.RecurringRuleOutDTO;
import com.lhj.jizhang.user.dto.RecurringRuleUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.RecurringExecutionEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Service
public class RecurringRuleService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "PAUSED", "COMPLETED");

    private final RecurringRuleMapper ruleMapper;
    private final RecurringExecutionMapper executionMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    /**
     * 初始化重复规则服务。
     *
     * @param ruleMapper 数据访问器
     * @param executionMapper 数据访问器
     * @param accountMapper 数据访问器
     * @param categoryMapper 数据访问器
     * @param bookMapper 数据访问器
     * @param bookAccessService 关联服务
     */
    public RecurringRuleService(
            RecurringRuleMapper ruleMapper,
            RecurringExecutionMapper executionMapper,
            AccountMapper accountMapper,
            CategoryMapper categoryMapper,
            BookMapper bookMapper,
            BookAccessService bookAccessService
    ) {
        this.ruleMapper = ruleMapper;
        this.executionMapper = executionMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 查询重复规则列表。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param status 状态
     * @return 重复规则列表
     */
    public List<RecurringRuleOutDTO> list(Long userId, Long bookId, String status) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.RECURRING);
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "周期规则状态不正确");
        }
        var query = Wrappers.<RecurringRuleEntity>lambdaQuery()
                .eq(RecurringRuleEntity::getBookId, bookId)
                .eq(RecurringRuleEntity::getDeletedFlag, 0)
                .orderByAsc(RecurringRuleEntity::getNextExecutionDate)
                .orderByDesc(RecurringRuleEntity::getId);
        if (status != null) {
            query.eq(RecurringRuleEntity::getStatus, status);
        }
        return ruleMapper.selectList(query).stream().map(rule -> toOutput(rule, List.of())).toList();
    }

    /**
     * 查询重复规则。
     *
     * @param userId 用户ID
     * @param ruleId 规则ID
     * @return 重复规则输出对象
     */
    public RecurringRuleOutDTO get(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRule(ruleId);
        bookAccessService.requireMenu(userId, rule.getBookId(), BookMenu.RECURRING);
        return toOutput(rule, recentExecutions(ruleId));
    }

    /**
     * 创建重复规则。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 重复规则输出对象
     */
    @Transactional
    public RecurringRuleOutDTO create(Long userId, RecurringRuleCreateInDTO input) {
        bookAccessService.requireAction(userId, input.bookId(), BookAction.RECURRING_MANAGE);
        BookEntity book = requireBook(input.bookId());
        validateDates(input.startDate(), input.endDate());
        validateRecurrence(input.recurrenceType(), input.executionMonth(), input.executionDay(), input.monthEnd());
        validateReferences(input.bookId(), input.transactionType(), input.categoryId(), input.accountId(),
                input.targetAccountId());
        RecurringRuleEntity rule = new RecurringRuleEntity();
        rule.setRuleNo(BusinessIdGenerator.next("RCR_"));
        rule.setBookId(input.bookId());
        rule.setCreatedUserId(userId);
        apply(rule, input.transactionType(), input.categoryId(), input.accountId(), input.targetAccountId(),
                input.amount(), input.title(), input.note(), input.recurrenceType(), input.executionMonth(), input.executionDay(),
                input.monthEnd(), input.executionTime(), input.startDate(), input.endDate(), input.executionMode());
        rule.setNextExecutionDate(nextFromToday(rule, book));
        rule.setStatus(rule.getNextExecutionDate() == null ? "COMPLETED" : "ACTIVE");
        rule.setVersion(0);
        rule.setDeletedFlag(0);
        rule.setCreator(String.valueOf(userId));
        rule.setModifier(String.valueOf(userId));
        ruleMapper.insert(rule);
        return toOutput(rule, List.of());
    }

    /**
     * 更新重复规则。
     *
     * @param userId 用户ID
     * @param ruleId 规则ID
     * @param input 请求参数
     * @return 重复规则输出对象
     */
    @Transactional
    public RecurringRuleOutDTO update(Long userId, Long ruleId, RecurringRuleUpdateInDTO input) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_MANAGE);
        if (!input.version().equals(rule.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "周期规则已发生变化，请刷新后重试");
        }
        validateDates(input.startDate(), input.endDate());
        validateRecurrence(input.recurrenceType(), input.executionMonth(), input.executionDay(), input.monthEnd());
        validateReferences(rule.getBookId(), input.transactionType(), input.categoryId(), input.accountId(),
                input.targetAccountId());
        apply(rule, input.transactionType(), input.categoryId(), input.accountId(), input.targetAccountId(),
                input.amount(), input.title(), input.note(), input.recurrenceType(), input.executionMonth(), input.executionDay(),
                input.monthEnd(), input.executionTime(), input.startDate(), input.endDate(), input.executionMode());
        rule.setNextExecutionDate(nextFromToday(rule, requireBook(rule.getBookId())));
        if (!"PAUSED".equals(rule.getStatus())) {
            rule.setStatus(rule.getNextExecutionDate() == null ? "COMPLETED" : "ACTIVE");
        }
        touch(rule, userId);
        ruleMapper.updateById(rule);
        return toOutput(rule, recentExecutions(ruleId));
    }

    /**
     * 暂停重复规则。
     *
     * @param userId 用户ID
     * @param ruleId 规则ID
     * @return 重复规则输出对象
     */
    @Transactional
    public RecurringRuleOutDTO pause(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_MANAGE);
        rule.setStatus("PAUSED");
        touch(rule, userId);
        ruleMapper.updateById(rule);
        return toOutput(rule, recentExecutions(ruleId));
    }

    /**
     * 恢复重复规则。
     *
     * @param userId 用户ID
     * @param ruleId 规则ID
     * @return 重复规则输出对象
     */
    @Transactional
    public RecurringRuleOutDTO resume(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_MANAGE);
        rule.setNextExecutionDate(nextAfterToday(rule, requireBook(rule.getBookId())));
        rule.setStatus(rule.getNextExecutionDate() == null ? "COMPLETED" : "ACTIVE");
        touch(rule, userId);
        ruleMapper.updateById(rule);
        return toOutput(rule, recentExecutions(ruleId));
    }

    /**
     * 删除重复规则。
     *
     * @param userId 用户ID
     * @param ruleId 规则ID
     */
    @Transactional
    public void delete(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_MANAGE);
        rule.setDeletedFlag(1);
        rule.setStatus("COMPLETED");
        rule.setNextExecutionDate(null);
        touch(rule, userId);
        ruleMapper.updateById(rule);
    }

    /**
     * 查询并校验规则。
     *
     * @param ruleId 规则ID
     * @return 重复规则实体
     */
    RecurringRuleEntity requireRule(Long ruleId) {
        RecurringRuleEntity rule = ruleMapper.selectById(ruleId);
        if (rule == null || rule.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期规则不存在");
        }
        return rule;
    }

    /**
     * 锁定并校验待修改的重复规则。
     *
     * @param ruleId 规则ID
     * @return 重复规则实体
     */
    private RecurringRuleEntity requireRuleForUpdate(Long ruleId) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        if (rule == null || rule.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期规则不存在");
        }
        return rule;
    }

    /**
     * 校验规则引用的分类和账户。
     *
     * @param bookId 账本ID
     * @param type 类型
     * @param categoryId 分类ID
     * @param accountId 账户ID
     * @param targetAccountId 目标账户ID
     */
    private void validateReferences(Long bookId, String type, Long categoryId, Long accountId, Long targetAccountId) {
        validateAccount(bookId, accountId);
        if ("TRANSFER".equals(type)) {
            if (targetAccountId == null || accountId.equals(targetAccountId) || categoryId != null) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "转账规则必须选择不同的转入账户且不能选择分类");
            }
            validateAccount(bookId, targetAccountId);
            return;
        }
        if (categoryId == null) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "收支规则必须选择分类");
        }
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null || !bookId.equals(category.getBookId()) || !type.equals(category.getCategoryType())
                || category.getHiddenFlag() != 0 || category.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类不存在或不可用");
        }
    }

    /**
     * 校验账户。
     *
     * @param bookId 账本ID
     * @param accountId 账户ID
     */
    private void validateAccount(Long bookId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !bookId.equals(account.getBookId()) || account.getStatus() != 1
                || account.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
        }
    }

    /**
     * 校验规则的起止日期。
     *
     * @param startDate 起始日期
     * @param endDate 结束日期
     */
    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "结束日期不能早于开始日期");
        }
    }

    /** 校验周期对应的月份、日期和月末选项。 */
    private void validateRecurrence(String type, Integer month, Integer day, Boolean monthEnd) {
        boolean valid = type != null && day != null && day >= 1 && day <= 31;
        if ("DAILY".equals(type)) {
            valid = month == null && day == null && Boolean.FALSE.equals(monthEnd);
        } else if ("MONTHLY".equals(type)) {
            valid = valid && month == null && monthEnd != null;
        } else if ("YEARLY".equals(type)) {
            valid = valid && month != null && month >= 1 && month <= 12
                    && Boolean.FALSE.equals(monthEnd)
                    && day <= YearMonth.of(2024, month).lengthOfMonth();
        } else {
            valid = false;
        }
        if (!valid) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "周期执行日期不正确");
        }
    }

    /**
     * 将请求字段应用到重复规则实体。
     *
     * @param rule 规则实体
     * @param type 类型
     * @param categoryId 分类ID
     * @param accountId 账户ID
     * @param targetAccountId 目标账户ID
     * @param amount 金额
     * @param title 标题
     * @param note 备注
     * @param recurrenceType 重复周期类型
     * @param executionMonth 每年执行月份
     * @param executionDay 每月执行日
     * @param monthEnd 是否启用
     * @param executionTime 计划执行时间
     * @param startDate 起始日期
     * @param endDate 结束日期
     * @param executionMode 执行模式
     */
    private void apply(RecurringRuleEntity rule, String type, Long categoryId, Long accountId,
                       Long targetAccountId, java.math.BigDecimal amount, String title, String note,
                       String recurrenceType, Integer executionMonth, Integer executionDay, Boolean monthEnd,
                       java.time.LocalTime executionTime, LocalDate startDate, LocalDate endDate,
                       String executionMode) {
        rule.setTransactionType(type);
        rule.setCategoryId(categoryId);
        rule.setAccountId(accountId);
        rule.setTargetAccountId(targetAccountId);
        rule.setAmount(amount);
        rule.setTitle(title.trim());
        rule.setNote(note);
        rule.setRecurrenceType(recurrenceType);
        rule.setExecutionMonth(executionMonth);
        rule.setExecutionDay(executionDay);
        rule.setMonthEndFlag(Boolean.TRUE.equals(monthEnd) ? 1 : 0);
        rule.setExecutionTime(executionTime);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        rule.setExecutionMode(executionMode);
    }

    /**
     * 计算不早于今天的下次执行日期。
     *
     * @param rule 规则实体
     * @param book 账本实体
     * @return 计算后的日期
     */
    private LocalDate nextFromToday(RecurringRuleEntity rule, BookEntity book) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(book.getTimezone()));
        LocalDate from = !"MONTHLY".equals(rule.getRecurrenceType())
                && now.toLocalTime().isAfter(rule.getExecutionTime())
                ? now.toLocalDate().plusDays(1) : now.toLocalDate();
        return RecurringDateCalculator.nextDate(from, rule);
    }

    /**
     * 计算晚于今天的下次执行日期。
     *
     * @param rule 规则实体
     * @param book 账本实体
     * @return 计算后的日期
     */
    private LocalDate nextAfterToday(RecurringRuleEntity rule, BookEntity book) {
        LocalDate tomorrow = LocalDate.now(ZoneId.of(book.getTimezone())).plusDays(1);
        return RecurringDateCalculator.nextDate(tomorrow, rule);
    }

    /**
     * 更新规则的修改人和版本号。
     *
     * @param rule 规则实体
     * @param userId 用户ID
     */
    private void touch(RecurringRuleEntity rule, Long userId) {
        rule.setVersion(rule.getVersion() + 1);
        rule.setModifier(String.valueOf(userId));
    }

    /**
     * 查询并校验账本存在。
     *
     * @param bookId 账本ID
     * @return 重复规则实体
     */
    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    /**
     * 查询规则最近的执行记录。
     *
     * @param ruleId 规则ID
     * @return 重复规则列表
     */
    private List<RecurringExecutionOutDTO> recentExecutions(Long ruleId) {
        RecurringRuleEntity rule = requireRule(ruleId);
        return executionMapper.selectList(Wrappers.<RecurringExecutionEntity>lambdaQuery()
                        .eq(RecurringExecutionEntity::getRuleId, ruleId)
                        .orderByDesc(RecurringExecutionEntity::getScheduledDate,
                                RecurringExecutionEntity::getId)
                        .last("LIMIT 20")).stream()
                .map(execution -> toExecution(execution, rule)).toList();
    }

    /**
     * 转换为重复规则输出对象。
     *
     * @param rule 规则实体
     * @param executions 执行记录集合
     * @return 重复规则输出对象
     */
    RecurringRuleOutDTO toOutput(RecurringRuleEntity rule, List<RecurringExecutionOutDTO> executions) {
        return new RecurringRuleOutDTO(rule.getId(), rule.getRuleNo(), rule.getBookId(), rule.getCreatedUserId(),
                rule.getTransactionType(), rule.getCategoryId(), rule.getAccountId(), rule.getTargetAccountId(),
                rule.getAmount(), rule.getTitle(), rule.getNote(), rule.getRecurrenceType(), rule.getExecutionMonth(), rule.getExecutionDay(),
                rule.getMonthEndFlag() == 1, rule.getExecutionTime(), rule.getStartDate(), rule.getEndDate(),
                rule.getNextExecutionDate(), rule.getLastExecutionDate(), rule.getExecutionMode(), rule.getStatus(),
                rule.getVersion(), rule.getCreatedTime() == null ? null : rule.getCreatedTime().toInstant(ZoneOffset.UTC),
                executions);
    }

    /**
     * 转换为执行记录。
     *
     * @param execution 执行记录实体
     * @param rule 规则实体
     * @return 重复规则输出对象
     */
    RecurringExecutionOutDTO toExecution(RecurringExecutionEntity execution, RecurringRuleEntity rule) {
        return new RecurringExecutionOutDTO(execution.getId(), execution.getRuleId(), rule.getTitle(),
                rule.getTransactionType(), rule.getAmount(), execution.getScheduledDate(), execution.getTransactionId(),
                execution.getExecutionStatus(), execution.getFailureReason(),
                execution.getExecutedTime() == null ? null : execution.getExecutedTime().toInstant(ZoneOffset.UTC),
                execution.getHandledUserId());
    }
}
