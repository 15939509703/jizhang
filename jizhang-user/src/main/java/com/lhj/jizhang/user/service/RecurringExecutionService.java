package com.lhj.jizhang.user.service;

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

    @Autowired(required = false)
    void setSubscriptionService(WechatSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public List<RecurringExecutionOutDTO> list(Long userId, Long bookId, String status, Integer limit) {
        bookAccessService.requireMember(userId, bookId);
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

    public long pendingCount(Long userId, Long bookId) {
        bookAccessService.requireMember(userId, bookId);
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

    public RecurringExecutionOutDTO get(Long userId, Long executionId) {
        RecurringExecutionEntity execution = executionMapper.selectById(executionId);
        if (execution == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期执行记录不存在");
        }
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireMember(userId, rule.getBookId());
        return ruleService.toExecution(execution, rule);
    }

    @Transactional
    public RecurringExecutionOutDTO processScheduled(Long ruleId, LocalDate scheduledDate) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        if (!canProcess(rule, scheduledDate)) {
            return null;
        }
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

    @Transactional
    public RecurringExecutionOutDTO executeNow(Long userId, Long ruleId) {
        RecurringRuleEntity rule = ruleMapper.selectByIdForUpdate(ruleId);
        requireAvailableRule(rule);
        bookAccessService.requireWritable(userId, rule.getBookId());
        if (!"ACTIVE".equals(rule.getStatus()) || rule.getNextExecutionDate() == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前规则不可立即执行");
        }
        return processScheduled(ruleId, rule.getNextExecutionDate());
    }

    @Transactional
    public RecurringExecutionOutDTO confirm(Long userId, Long executionId, RecurringConfirmInDTO input) {
        RecurringExecutionEntity execution = requireExecutionForUpdate(executionId);
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireWritable(userId, rule.getBookId());
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

    @Transactional
    public RecurringExecutionOutDTO skip(Long userId, Long executionId) {
        RecurringExecutionEntity execution = requireExecutionForUpdate(executionId);
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireWritable(userId, rule.getBookId());
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

    @Transactional
    public RecurringExecutionOutDTO retry(Long userId, Long executionId) {
        RecurringExecutionEntity execution = requireExecutionForUpdate(executionId);
        RecurringRuleEntity rule = ruleService.requireRule(execution.getRuleId());
        bookAccessService.requireWritable(userId, rule.getBookId());
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

    private boolean canProcess(RecurringRuleEntity rule, LocalDate scheduledDate) {
        return rule != null && rule.getDeletedFlag() == 0 && "ACTIVE".equals(rule.getStatus())
                && scheduledDate != null && scheduledDate.equals(rule.getNextExecutionDate());
    }

    private RecurringExecutionEntity createExecution(RecurringRuleEntity rule, LocalDate scheduledDate) {
        RecurringExecutionEntity execution = newExecution(rule, scheduledDate,
                "CONFIRM".equals(rule.getExecutionMode()) ? "PENDING" : "PROCESSING");
        if (executionMapper.insertIgnore(execution) != 1) return null;
        if ("PENDING".equals(execution.getExecutionStatus()) && subscriptionService != null) {
            BookEntity book = bookMapper.selectById(rule.getBookId());
            subscriptionService.enqueue("RECUR_PENDING_" + execution.getId(), rule.getCreatedUserId(),
                    "RECURRING_PENDING", "/pages/recurring-confirm/recurring-confirm?id=" + execution.getId(),
                    Map.of("thing1", Map.of("value", crop(book.getName(), 20)),
                            "thing2", Map.of("value", crop(rule.getTitle(), 20)),
                            "date3", Map.of("value", scheduledDate.toString())));
        }
        return execution;
    }

    private String crop(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private RecurringExecutionEntity newExecution(RecurringRuleEntity rule, LocalDate scheduledDate, String status) {
        RecurringExecutionEntity execution = new RecurringExecutionEntity();
        execution.setRuleId(rule.getId());
        execution.setScheduledDate(scheduledDate);
        execution.setExecutionStatus(status);
        execution.setCreator(String.valueOf(rule.getCreatedUserId()));
        execution.setModifier(String.valueOf(rule.getCreatedUserId()));
        return execution;
    }

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

    private <T> T value(RecurringConfirmInDTO override,
                        Function<RecurringConfirmInDTO, T> getter, T fallback) {
        if (override == null) {
            return fallback;
        }
        T value = getter.apply(override);
        return value == null ? fallback : value;
    }

    private String textValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Instant scheduledInstant(RecurringRuleEntity rule, LocalDate date, BookEntity book) {
        if (book == null) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在");
        }
        return date.atTime(rule.getExecutionTime()).atZone(ZoneId.of(book.getTimezone())).toInstant();
    }

    private void advance(RecurringRuleEntity rule, LocalDate scheduledDate) {
        LocalDate next = MonthlyRecurrenceCalculator.nextDate(scheduledDate.plusDays(1), rule.getStartDate(),
                rule.getEndDate(), rule.getExecutionDay(), rule.getMonthEndFlag() == 1);
        rule.setLastExecutionDate(scheduledDate);
        rule.setNextExecutionDate(next);
        rule.setStatus(next == null ? "COMPLETED" : "ACTIVE");
        rule.setVersion(rule.getVersion() + 1);
        rule.setModifier("system");
        ruleMapper.updateById(rule);
    }

    private RecurringExecutionEntity requireExecutionForUpdate(Long executionId) {
        RecurringExecutionEntity execution = executionMapper.selectByIdForUpdate(executionId);
        if (execution == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期执行记录不存在");
        }
        return execution;
    }

    private RecurringExecutionEntity findExecution(Long ruleId, LocalDate scheduledDate) {
        return executionMapper.selectOne(Wrappers.<RecurringExecutionEntity>lambdaQuery()
                .eq(RecurringExecutionEntity::getRuleId, ruleId)
                .eq(RecurringExecutionEntity::getScheduledDate, scheduledDate));
    }

    private void requireAvailableRule(RecurringRuleEntity rule) {
        if (rule == null || rule.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "周期规则不存在");
        }
    }

    private String readableMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "执行失败，请稍后重试";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
