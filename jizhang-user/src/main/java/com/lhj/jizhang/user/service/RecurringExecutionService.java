package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.BookMenu;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.RecurringConfirmInDTO;
import com.lhj.jizhang.user.dto.RecurringExecutionOutDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringExecutionEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecurringExecutionService {
    private static final Set<String> EXECUTION_STATUSES =
            Set.of("PENDING", "PROCESSING", "SUCCESS", "SKIPPED", "FAILED");

    private final RecurringExecutionMapper executionMapper;
    private final RecurringRuleMapper ruleMapper;
    private final BookMapper bookMapper;
    private final RecurringRuleService ruleService;
    private final TransactionService transactionService;
    private final BookAccessService bookAccessService;
    private WechatSubscriptionService subscriptionService;

    /**
     * 初始化重复规则执行服务。
     *
     * @param executionMapper 数据访问器
     * @param ruleMapper 数据访问器
     * @param bookMapper 数据访问器
     * @param ruleService 关联服务
     * @param transactionService 关联服务
     * @param bookAccessService 关联服务
     */
    public RecurringExecutionService(
            RecurringExecutionMapper executionMapper,
            RecurringRuleMapper ruleMapper,
            BookMapper bookMapper,
            RecurringRuleService ruleService,
            TransactionService transactionService,
            BookAccessService bookAccessService
    ) {
        this.executionMapper = executionMapper;
        this.ruleMapper = ruleMapper;
        this.bookMapper = bookMapper;
        this.ruleService = ruleService;
        this.transactionService = transactionService;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 设置重复规则通知服务。
     *
     * @param subscriptionService 关联服务
     */
    @Autowired(required = false)
    void setSubscriptionService(WechatSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * 查询重复规则执行列表。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param status 状态
     * @param limit 分页条数
     * @return 重复规则执行列表
     */
    public List<RecurringExecutionOutDTO> list(Long userId, Long bookId, String status, Integer limit) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.RECURRING);
        int pageSize = limit == null ? 50 : limit;
        if (pageSize < 1 || pageSize > 100 || status != null && !EXECUTION_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "执行记录查询参数不正确");
        }
        List<RecurringRuleEntity> rules = ruleMapper.selectList(Wrappers.<RecurringRuleEntity>lambdaQuery()
                .eq(RecurringRuleEntity::getBookId, bookId));
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<Long, RecurringRuleEntity> ruleMap = rules.stream()
                .collect(Collectors.toMap(RecurringRuleEntity::getId, Function.identity()));
        var query = Wrappers.<RecurringExecutionEntity>lambdaQuery()
                .in(RecurringExecutionEntity::getRuleId, ruleMap.keySet())
                .orderByDesc(RecurringExecutionEntity::getScheduledDate, RecurringExecutionEntity::getId)
                .last("LIMIT " + pageSize);
        if (status != null) {
            query.eq(RecurringExecutionEntity::getExecutionStatus, status);
        }
        return executionMapper.selectList(query).stream()
                .map(item -> ruleService.toExecution(item, ruleMap.get(item.getRuleId()))).toList();
    }

    /**
     * 统计账本中待确认的重复规则执行数。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @return 待确认执行记录数
     */
    public long pendingCount(Long userId, Long bookId) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.RECURRING);
        List<Long> ruleIds = ruleMapper.selectList(Wrappers.<RecurringRuleEntity>lambdaQuery()
                        .select(RecurringRuleEntity::getId)
                        .eq(RecurringRuleEntity::getBookId, bookId)
                        .eq(RecurringRuleEntity::getDeletedFlag, 0)).stream()
                .map(RecurringRuleEntity::getId).toList();
        if (ruleIds.isEmpty()) {
            return 0;
        }
        return executionMapper.selectCount(Wrappers.<RecurringExecutionEntity>lambdaQuery()
                .in(RecurringExecutionEntity::getRuleId, ruleIds)
                .eq(RecurringExecutionEntity::getExecutionStatus, "PENDING"));
    }

    /**
     * 查询重复规则执行。
     *
     * @param userId 用户ID
     * @param executionId 执行记录ID
     * @return 重复规则执行输出对象
     */
    public RecurringExecutionOutDTO get(Long userId, Long executionId) {
        RecurringExecutionEntity execution = executionMapper.selectById(executionId);
        if (execution == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期执行记录不存在");
        }
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireMenu(userId, rule.getBookId(), BookMenu.RECURRING);
        return ruleService.toExecution(execution, rule);
    }

    /**
     * 处理指定日期到期的重复规则。
     *
     * @param ruleId 规则ID
     * @param scheduledDate 计划执行日期
     * @return 重复规则执行输出对象
     */
    @Transactional
    public RecurringExecutionOutDTO processScheduled(Long ruleId, LocalDate scheduledDate) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        if (!canProcess(rule, scheduledDate)) {
            return null;
        }
        bookAccessService.requireAction(rule.getCreatedUserId(), rule.getBookId(), BookAction.RECURRING_MANAGE);
        RecurringExecutionEntity execution = createExecution(rule, scheduledDate);
        if (execution == null) {
            advance(rule, scheduledDate);
            return null;
        }
        if ("AUTO".equals(rule.getExecutionMode())) {
            execution.setExecutionStatus("PROCESSING");
            executionMapper.updateById(execution);
            createTransaction(rule, execution, null);
        }
        advance(rule, scheduledDate);
        return ruleService.toExecution(execution, rule);
    }

    /**
     * 立即执行一条重复规则。
     *
     * @param userId 用户ID
     * @param ruleId 规则ID
     * @return 重复规则执行输出对象
     */
    @Transactional
    public RecurringExecutionOutDTO executeNow(Long userId, Long ruleId) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        requireAvailableRule(rule);
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_MANAGE);
        if (!"ACTIVE".equals(rule.getStatus()) || rule.getNextExecutionDate() == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前规则不可立即执行");
        }
        return processScheduled(ruleId, rule.getNextExecutionDate());
    }

    /**
     * 确认重复规则执行。
     *
     * @param userId 用户ID
     * @param executionId 执行记录ID
     * @param input 请求参数
     * @return 重复规则执行输出对象
     */
    @Transactional
    public RecurringExecutionOutDTO confirm(Long userId, Long executionId, RecurringConfirmInDTO input) {
        RecurringExecutionEntity execution = requireExecutionForUpdate(executionId);
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_CONFIRM);
        if (!"PENDING".equals(execution.getExecutionStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前执行项不允许确认");
        }
        execution.setExecutionStatus("PROCESSING");
        execution.setHandledUserId(userId);
        execution.setModifier(String.valueOf(userId));
        executionMapper.updateById(execution);
        createTransaction(rule, execution, input);
        return ruleService.toExecution(execution, rule);
    }

    /**
     * 跳过重复规则执行。
     *
     * @param userId 用户ID
     * @param executionId 执行记录ID
     * @return 重复规则执行输出对象
     */
    @Transactional
    public RecurringExecutionOutDTO skip(Long userId, Long executionId) {
        RecurringExecutionEntity execution = requireExecutionForUpdate(executionId);
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_CONFIRM);
        if (!"PENDING".equals(execution.getExecutionStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前执行项不允许跳过");
        }
        execution.setExecutionStatus("SKIPPED");
        execution.setExecutedTime(LocalDateTime.now(ZoneOffset.UTC));
        execution.setHandledUserId(userId);
        execution.setModifier(String.valueOf(userId));
        executionMapper.updateById(execution);
        return ruleService.toExecution(execution, rule);
    }

    /**
     * 重试重复规则执行。
     *
     * @param userId 用户ID
     * @param executionId 执行记录ID
     * @return 重复规则执行输出对象
     */
    @Transactional
    public RecurringExecutionOutDTO retry(Long userId, Long executionId) {
        RecurringExecutionEntity execution = requireExecutionForUpdate(executionId);
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireAction(userId, rule.getBookId(), BookAction.RECURRING_CONFIRM);
        if (!"FAILED".equals(execution.getExecutionStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "仅失败执行项可以重试");
        }
        execution.setExecutionStatus("PROCESSING");
        execution.setFailureReason(null);
        execution.setHandledUserId(userId);
        execution.setModifier(String.valueOf(userId));
        executionMapper.updateById(execution);
        createTransaction(rule, execution, null);
        return ruleService.toExecution(execution, rule);
    }

    /**
     * 记录规则执行失败并更新状态。
     *
     * @param ruleId 规则ID
     * @param scheduledDate 计划执行日期
     * @param exception 失败异常
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long ruleId, LocalDate scheduledDate, RuntimeException exception) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        if (rule == null || rule.getDeletedFlag() != 0) {
            return;
        }
        RecurringExecutionEntity execution = findExecution(ruleId, scheduledDate);
        if (execution == null) {
            execution = newExecution(rule, scheduledDate, "FAILED");
            executionMapper.insert(execution);
        } else if ("SUCCESS".equals(execution.getExecutionStatus())
                || "SKIPPED".equals(execution.getExecutionStatus())) {
            return;
        }
        execution.setExecutionStatus("FAILED");
        execution.setFailureReason(readableMessage(exception));
        execution.setExecutedTime(LocalDateTime.now(ZoneOffset.UTC));
        execution.setModifier("system");
        executionMapper.updateById(execution);
        if (exception instanceof BusinessException businessException
                && ErrorCodes.BOOK_ACCESS_DENIED.equals(businessException.getCode())) {
            rule.setStatus("PAUSED");
            rule.setNextExecutionDate(null);
        } else {
            advance(rule, scheduledDate);
        }
        ruleMapper.updateById(rule);
    }

    /**
     * 判断规则在指定日期是否可以执行。
     *
     * @param rule 规则实体
     * @param scheduledDate 计划执行日期
     * @return 条件是否成立
     */
    private boolean canProcess(RecurringRuleEntity rule, LocalDate scheduledDate) {
        return rule != null && rule.getDeletedFlag() == 0 && "ACTIVE".equals(rule.getStatus())
                && scheduledDate != null && scheduledDate.equals(rule.getNextExecutionDate());
    }

    /**
     * 创建执行记录。
     *
     * @param rule 规则实体
     * @param scheduledDate 计划执行日期
     * @return 重复规则执行实体
     */
    private RecurringExecutionEntity createExecution(RecurringRuleEntity rule, LocalDate scheduledDate) {
        RecurringExecutionEntity execution = newExecution(rule, scheduledDate,
                "CONFIRM".equals(rule.getExecutionMode()) ? "PENDING" : "PROCESSING");
        if (executionMapper.insertIgnore(execution) != 1) return null;
        if ("PENDING".equals(execution.getExecutionStatus()) && subscriptionService != null) {
            BookEntity book = bookMapper.selectById(rule.getBookId());
            subscriptionService.enqueue("RECUR_PENDING_" + execution.getId(), rule.getCreatedUserId(), rule.getBookId(),
                    "RECURRING_PENDING", "/pages/recurring-confirm/recurring-confirm?id=" + execution.getId(),
                    Map.of("thing1", Map.of("value", crop(book.getName(), 20)),
                            "thing2", Map.of("value", crop(rule.getTitle(), 20)),
                            "date3", Map.of("value", scheduledDate.toString())));
        }
        return execution;
    }

    /**
     * 截断超出长度限制的执行提示。
     *
     * @param value 原始值
     * @param max 最大长度
     * @return 处理后的文本
     */
    private String crop(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 构建重复规则执行记录。
     *
     * @param rule 规则实体
     * @param scheduledDate 计划执行日期
     * @param status 状态
     * @return 重复规则执行实体
     */
    private RecurringExecutionEntity newExecution(RecurringRuleEntity rule, LocalDate scheduledDate, String status) {
        RecurringExecutionEntity execution = new RecurringExecutionEntity();
        execution.setRuleId(rule.getId());
        execution.setScheduledDate(scheduledDate);
        execution.setExecutionStatus(status);
        execution.setCreator(String.valueOf(rule.getCreatedUserId()));
        execution.setModifier(String.valueOf(rule.getCreatedUserId()));
        return execution;
    }

    /**
     * 根据重复规则执行记录创建账单。
     *
     * @param rule 规则实体
     * @param execution 执行记录实体
     * @param override 业务数据
     */
    private void createTransaction(
            RecurringRuleEntity rule,
            RecurringExecutionEntity execution,
            RecurringConfirmInDTO override
    ) {
        TransactionOutDTO transaction = transactionService.create(rule.getCreatedUserId(),
                transactionCommand(rule, execution, override));
        execution.setTransactionId(transaction.id());
        execution.setExecutionStatus("SUCCESS");
        execution.setFailureReason(null);
        execution.setExecutedTime(LocalDateTime.now(ZoneOffset.UTC));
        executionMapper.updateById(execution);
    }

    /**
     * 根据执行记录生成账单创建参数。
     *
     * @param rule 规则实体
     * @param execution 执行记录实体
     * @param override 业务数据
     * @return 重复规则执行请求参数
     */
    private TransactionCreateInDTO transactionCommand(
            RecurringRuleEntity rule,
            RecurringExecutionEntity execution,
            RecurringConfirmInDTO override
    ) {
        BookEntity book = bookMapper.selectById(rule.getBookId());
        Instant happenedAt = override != null && override.happenedAt() != null
                ? override.happenedAt() : scheduledInstant(rule, execution.getScheduledDate(), book);
        return new TransactionCreateInDTO(
                "RECUR_" + rule.getId() + "_" + execution.getScheduledDate().toString().replace("-", ""),
                rule.getBookId(), rule.getTransactionType(), value(override, RecurringConfirmInDTO::categoryId,
                rule.getCategoryId()), null, value(override, RecurringConfirmInDTO::accountId, rule.getAccountId()),
                value(override, RecurringConfirmInDTO::targetAccountId, rule.getTargetAccountId()),
                value(override, RecurringConfirmInDTO::amount, rule.getAmount()), happenedAt,
                textValue(override == null ? null : override.title(), rule.getTitle()),
                override != null && override.note() != null ? override.note() : rule.getNote());
    }

    /**
     * 读取确认参数，缺失时使用默认值。
     *
     * @param override 业务数据
     * @param getter 字段取值函数
     * @param fallback 默认值
     * @return 重复规则执行结果
     */
    private <T> T value(RecurringConfirmInDTO override,
                        Function<RecurringConfirmInDTO, T> getter, T fallback) {
        if (override == null) {
            return fallback;
        }
        T value = getter.apply(override);
        return value == null ? fallback : value;
    }

    /**
     * 读取非空文本，缺失时使用默认值。
     *
     * @param value 原始值
     * @param fallback 默认值
     * @return 处理后的文本
     */
    private String textValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * 计算规则在账本时区中的计划执行时间。
     *
     * @param rule 规则实体
     * @param date 目标日期
     * @param book 账本实体
     * @return 转换后的时间
     */
    private Instant scheduledInstant(RecurringRuleEntity rule, LocalDate date, BookEntity book) {
        if (book == null) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在");
        }
        return date.atTime(rule.getExecutionTime()).atZone(ZoneId.of(book.getTimezone())).toInstant();
    }

    /**
     * 推进规则的下一次执行日期。
     *
     * @param rule 规则实体
     * @param scheduledDate 计划执行日期
     */
    private void advance(RecurringRuleEntity rule, LocalDate scheduledDate) {
        LocalDate next = RecurringDateCalculator.nextDate(scheduledDate.plusDays(1), rule);
        rule.setLastExecutionDate(scheduledDate);
        rule.setNextExecutionDate(next);
        rule.setStatus(next == null ? "COMPLETED" : "ACTIVE");
        rule.setVersion(rule.getVersion() + 1);
        rule.setModifier("system");
        ruleMapper.updateById(rule);
    }

    /**
     * 锁定并校验待修改的执行记录。
     *
     * @param executionId 执行记录ID
     * @return 重复规则执行实体
     */
    private RecurringExecutionEntity requireExecutionForUpdate(Long executionId) {
        RecurringExecutionEntity execution = executionMapper.selectByIdForUpdate(executionId);
        if (execution == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期执行记录不存在");
        }
        return execution;
    }

    /**
     * 查询执行记录。
     *
     * @param ruleId 规则ID
     * @param scheduledDate 计划执行日期
     * @return 重复规则执行实体
     */
    private RecurringExecutionEntity findExecution(Long ruleId, LocalDate scheduledDate) {
        return executionMapper.selectOne(Wrappers.<RecurringExecutionEntity>lambdaQuery()
                .eq(RecurringExecutionEntity::getRuleId, ruleId)
                .eq(RecurringExecutionEntity::getScheduledDate, scheduledDate));
    }

    /**
     * 校验规则当前可执行。
     *
     * @param rule 规则实体
     */
    private void requireAvailableRule(RecurringRuleEntity rule) {
        if (rule == null || rule.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期规则不存在");
        }
    }

    /**
     * 提取适合展示的执行失败原因。
     *
     * @param exception 失败异常
     * @return 处理后的文本
     */
    private String readableMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "执行失败，请稍后重试";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
