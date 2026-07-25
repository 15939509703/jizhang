package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "jizhang.features", name = "recurring-transactions", havingValue = "true",
        matchIfMissing = true)
public class RecurringScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecurringScheduler.class);

    private final RecurringRuleMapper ruleMapper;
    private final BookMapper bookMapper;
    private final RecurringExecutionService executionService;

    public RecurringScheduler(RecurringRuleMapper ruleMapper, BookMapper bookMapper,
                              RecurringExecutionService executionService) {
        this.ruleMapper = ruleMapper;
        this.bookMapper = bookMapper;
        this.executionService = executionService;
    }

    @Scheduled(fixedDelayString = "${jizhang.recurring.scan-interval-ms:300000}", initialDelay = 30000)
    public void scan() {
        List<RecurringRuleEntity> candidates = ruleMapper.selectList(Wrappers.<RecurringRuleEntity>lambdaQuery()
                .eq(RecurringRuleEntity::getStatus, "ACTIVE")
                .eq(RecurringRuleEntity::getDeletedFlag, 0)
                .le(RecurringRuleEntity::getNextExecutionDate, LocalDate.now().plusDays(1))
                .orderByAsc(RecurringRuleEntity::getNextExecutionDate, RecurringRuleEntity::getId)
                .last("LIMIT 100"));
        Instant now = Instant.now();
        for (RecurringRuleEntity rule : candidates) {
            processIfDue(rule, now);
        }
    }

    private void processIfDue(RecurringRuleEntity rule, Instant now) {
        BookEntity book = bookMapper.selectById(rule.getBookId());
        if (book == null || rule.getNextExecutionDate() == null) {
            return;
        }
        Instant dueAt = rule.getNextExecutionDate().atTime(rule.getExecutionTime())
                .atZone(ZoneId.of(book.getTimezone())).toInstant();
        if (dueAt.isAfter(now)) {
            return;
        }
        LocalDate scheduledDate = rule.getNextExecutionDate();
        try {
            executionService.processScheduled(rule.getId(), scheduledDate);
        } catch (RuntimeException exception) {
            LOGGER.warn("Recurring execution failed: ruleNo={}, scheduledDate={}",
                    rule.getRuleNo(), scheduledDate);
            executionService.recordFailure(rule.getId(), scheduledDate, exception);
        }
    }
}
