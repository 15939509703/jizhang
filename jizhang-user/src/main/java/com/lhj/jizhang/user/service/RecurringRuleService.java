package com.lhj.jizhang.user.service;

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
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    public List<RecurringRuleOutDTO> list(Long userId, Long bookId, String status) {
        bookAccessService.requireMember(userId, bookId);
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

    public RecurringRuleOutDTO get(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRule(ruleId);
        bookAccessService.requireMember(userId, rule.getBookId());
        return toOutput(rule, recentExecutions(ruleId));
    }

    @Transactional
    public RecurringRuleOutDTO create(Long userId, RecurringRuleCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        BookEntity book = requireBook(input.bookId());
        validateDates(input.startDate(), input.endDate());
        validateReferences(input.bookId(), input.transactionType(), input.categoryId(), input.accountId(),
                input.targetAccountId());
        RecurringRuleEntity rule = new RecurringRuleEntity();
        rule.setRuleNo(BusinessIdGenerator.next("RCR_"));
        rule.setBookId(input.bookId());
        rule.setCreatedUserId(userId);
        apply(rule, input.transactionType(), input.categoryId(), input.accountId(), input.targetAccountId(),
                input.amount(), input.title(), input.note(), input.recurrenceType(), input.executionDay(),
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

    @Transactional
    public RecurringRuleOutDTO update(Long userId, Long ruleId, RecurringRuleUpdateInDTO input) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireWritable(userId, rule.getBookId());
        if (!input.version().equals(rule.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "周期规则已发生变化，请刷新后重试");
        }
        validateDates(input.startDate(), input.endDate());
        validateReferences(rule.getBookId(), input.transactionType(), input.categoryId(), input.accountId(),
                input.targetAccountId());
        apply(rule, input.transactionType(), input.categoryId(), input.accountId(), input.targetAccountId(),
                input.amount(), input.title(), input.note(), input.recurrenceType(), input.executionDay(),
                input.monthEnd(), input.executionTime(), input.startDate(), input.endDate(), input.executionMode());
        rule.setNextExecutionDate(nextFromToday(rule, requireBook(rule.getBookId())));
        if (!"PAUSED".equals(rule.getStatus())) {
            rule.setStatus(rule.getNextExecutionDate() == null ? "COMPLETED" : "ACTIVE");
        }
        touch(rule, userId);
        ruleMapper.updateById(rule);
        return toOutput(rule, recentExecutions(ruleId));
    }

    @Transactional
    public RecurringRuleOutDTO pause(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireWritable(userId, rule.getBookId());
        rule.setStatus("PAUSED");
        touch(rule, userId);
        ruleMapper.updateById(rule);
        return toOutput(rule, recentExecutions(ruleId));
    }

    @Transactional
    public RecurringRuleOutDTO resume(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireWritable(userId, rule.getBookId());
        rule.setNextExecutionDate(nextAfterToday(rule, requireBook(rule.getBookId())));
        rule.setStatus(rule.getNextExecutionDate() == null ? "COMPLETED" : "ACTIVE");
        touch(rule, userId);
        ruleMapper.updateById(rule);
        return toOutput(rule, recentExecutions(ruleId));
    }

    @Transactional
    public void delete(Long userId, Long ruleId) {
        RecurringRuleEntity rule = requireRuleForUpdate(ruleId);
        bookAccessService.requireWritable(userId, rule.getBookId());
        rule.setDeletedFlag(1);
        rule.setStatus("COMPLETED");
        rule.setNextExecutionDate(null);
        touch(rule, userId);
        ruleMapper.updateById(rule);
    }

    RecurringRuleEntity requireRule(Long ruleId) {
        RecurringRuleEntity rule = ruleMapper.selectById(ruleId);
        if (rule == null || rule.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期规则不存在");
        }
        return rule;
    }

    private RecurringRuleEntity requireRuleForUpdate(Long ruleId) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        if (rule == null || rule.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期规则不存在");
        }
        return rule;
    }

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

    private void validateAccount(Long bookId, Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || !bookId.equals(account.getBookId()) || account.getStatus() != 1
                || account.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "结束日期不能早于开始日期");
        }
    }

    private void apply(RecurringRuleEntity rule, String type, Long categoryId, Long accountId,
                       Long targetAccountId, java.math.BigDecimal amount, String title, String note,
                       String recurrenceType, Integer executionDay, Boolean monthEnd,
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
        rule.setExecutionDay(executionDay);
        rule.setMonthEndFlag(Boolean.TRUE.equals(monthEnd) ? 1 : 0);
        rule.setExecutionTime(executionTime);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        rule.setExecutionMode(executionMode);
    }

    private LocalDate nextFromToday(RecurringRuleEntity rule, BookEntity book) {
        LocalDate today = LocalDate.now(ZoneId.of(book.getTimezone()));
        return MonthlyRecurrenceCalculator.nextDate(today, rule.getStartDate(), rule.getEndDate(),
                rule.getExecutionDay(), rule.getMonthEndFlag() == 1);
    }

    private LocalDate nextAfterToday(RecurringRuleEntity rule, BookEntity book) {
        LocalDate tomorrow = LocalDate.now(ZoneId.of(book.getTimezone())).plusDays(1);
        return MonthlyRecurrenceCalculator.nextDate(tomorrow, rule.getStartDate(), rule.getEndDate(),
                rule.getExecutionDay(), rule.getMonthEndFlag() == 1);
    }

    private void touch(RecurringRuleEntity rule, Long userId) {
        rule.setVersion(rule.getVersion() + 1);
        rule.setModifier(String.valueOf(userId));
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    private List<RecurringExecutionOutDTO> recentExecutions(Long ruleId) {
        RecurringRuleEntity rule = requireRule(ruleId);
        return executionMapper.selectList(Wrappers.<RecurringExecutionEntity>lambdaQuery()
                        .eq(RecurringExecutionEntity::getRuleId, ruleId)
                        .orderByDesc(RecurringExecutionEntity::getScheduledDate,
                                RecurringExecutionEntity::getId)
                        .last("LIMIT 20")).stream()
                .map(execution -> toExecution(execution, rule)).toList();
    }

    RecurringRuleOutDTO toOutput(RecurringRuleEntity rule, List<RecurringExecutionOutDTO> executions) {
        return new RecurringRuleOutDTO(rule.getId(), rule.getRuleNo(), rule.getBookId(), rule.getCreatedUserId(),
                rule.getTransactionType(), rule.getCategoryId(), rule.getAccountId(), rule.getTargetAccountId(),
                rule.getAmount(), rule.getTitle(), rule.getNote(), rule.getRecurrenceType(), rule.getExecutionDay(),
                rule.getMonthEndFlag() == 1, rule.getExecutionTime(), rule.getStartDate(), rule.getEndDate(),
                rule.getNextExecutionDate(), rule.getLastExecutionDate(), rule.getExecutionMode(), rule.getStatus(),
                rule.getVersion(), rule.getCreatedTime() == null ? null : rule.getCreatedTime().toInstant(ZoneOffset.UTC),
                executions);
    }

    RecurringExecutionOutDTO toExecution(RecurringExecutionEntity execution, RecurringRuleEntity rule) {
        return new RecurringExecutionOutDTO(execution.getId(), execution.getRuleId(), rule.getTitle(),
                rule.getTransactionType(), rule.getAmount(), execution.getScheduledDate(), execution.getTransactionId(),
                execution.getExecutionStatus(), execution.getFailureReason(),
                execution.getExecutedTime() == null ? null : execution.getExecutedTime().toInstant(ZoneOffset.UTC),
                execution.getHandledUserId());
    }
}
