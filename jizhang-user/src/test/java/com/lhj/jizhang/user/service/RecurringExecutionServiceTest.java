package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.RecurringExecutionOutDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringExecutionEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringExecutionServiceTest {
    @Mock
    private RecurringExecutionMapper executionMapper;
    @Mock
    private RecurringRuleMapper ruleMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private RecurringRuleService ruleService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private BookAccessService bookAccessService;

    private RecurringExecutionService service;

    @BeforeEach
    void setUp() {
        service = new RecurringExecutionService(executionMapper, ruleMapper, bookMapper,
                ruleService, transactionService, bookAccessService);
    }

    @Test
    void shouldNotCreateTransactionWhenExecutionWasAlreadyClaimed() {
        RecurringRuleEntity rule = rule("CONFIRM");
        when(ruleMapper.selectByIdForUpdate(10L)).thenReturn(rule);
        when(executionMapper.insertIgnore(any(RecurringExecutionEntity.class))).thenReturn(0);

        RecurringExecutionOutDTO result = service.processScheduled(10L, LocalDate.of(2026, 7, 15));

        assertNull(result);
        assertEquals(LocalDate.of(2026, 8, 15), rule.getNextExecutionDate());
        verify(transactionService, never()).create(any(), any());
        verify(ruleMapper).updateById(rule);
    }

    @Test
    void shouldCreateAutomaticTransactionWithStableRequestId() {
        RecurringRuleEntity rule = rule("AUTO");
        when(ruleMapper.selectByIdForUpdate(10L)).thenReturn(rule);
        doAnswer(invocation -> {
            invocation.<RecurringExecutionEntity>getArgument(0).setId(20L);
            return 1;
        }).when(executionMapper).insertIgnore(any(RecurringExecutionEntity.class));
        when(bookMapper.selectById(1L)).thenReturn(book());
        when(transactionService.create(any(), any())).thenReturn(transaction());
        when(ruleService.toExecution(any(), any())).thenReturn(new RecurringExecutionOutDTO(
                20L, 10L, "房租", "EXPENSE", new BigDecimal("1000.00"),
                LocalDate.of(2026, 7, 15), 99L, "SUCCESS", null, Instant.now(), null));

        RecurringExecutionOutDTO result = service.processScheduled(10L, LocalDate.of(2026, 7, 15));

        ArgumentCaptor<TransactionCreateInDTO> captor = ArgumentCaptor.forClass(TransactionCreateInDTO.class);
        verify(transactionService).create(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertEquals("RECUR_10_20260715", captor.getValue().requestId());
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), captor.getValue().happenedAt());
        assertEquals(99L, result.transactionId());
    }

    private RecurringRuleEntity rule(String mode) {
        RecurringRuleEntity rule = new RecurringRuleEntity();
        rule.setId(10L);
        rule.setBookId(1L);
        rule.setCreatedUserId(7L);
        rule.setTransactionType("EXPENSE");
        rule.setCategoryId(2L);
        rule.setAccountId(3L);
        rule.setAmount(new BigDecimal("1000.00"));
        rule.setTitle("房租");
        rule.setRecurrenceType("MONTHLY");
        rule.setExecutionDay(15);
        rule.setMonthEndFlag(0);
        rule.setExecutionTime(LocalTime.of(8, 0));
        rule.setStartDate(LocalDate.of(2026, 1, 1));
        rule.setNextExecutionDate(LocalDate.of(2026, 7, 15));
        rule.setExecutionMode(mode);
        rule.setStatus("ACTIVE");
        rule.setVersion(0);
        rule.setDeletedFlag(0);
        return rule;
    }

    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setTimezone("Asia/Shanghai");
        return book;
    }

    private TransactionOutDTO transaction() {
        return new TransactionOutDTO(99L, "TXN_TEST", "RECUR_10_20260715", 1L, 7L,
                "EXPENSE", 2L, null, new BigDecimal("1000.00"), "CNY",
                Instant.parse("2026-07-15T00:00:00Z"), "房租", null,
                "EFFECTIVE", 0, List.of(), List.of());
    }
}
