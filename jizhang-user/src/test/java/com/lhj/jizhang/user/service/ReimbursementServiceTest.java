package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.ReimbursementCreateInDTO;
import com.lhj.jizhang.user.dto.ReimbursementOutDTO;
import com.lhj.jizhang.user.dto.ReimbursementReceiveInDTO;
import com.lhj.jizhang.user.dto.ReimbursementUpdateInDTO;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.ReimbursementEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.ReimbursementMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReimbursementServiceTest {
    @Mock
    private ReimbursementMapper reimbursementMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private BookAccessService bookAccessService;

    private ReimbursementService service;

    @BeforeEach
    void setUp() {
        service = new ReimbursementService(reimbursementMapper, transactionMapper,
                transactionService, bookAccessService);
    }

    @Test
    void shouldUpdatePendingReimbursement() {
        ReimbursementEntity entity = reimbursement("PENDING");
        when(reimbursementMapper.selectByIdForUpdate(10L)).thenReturn(entity);
        ReimbursementUpdateInDTO input = new ReimbursementUpdateInDTO(" 研发中心 ",
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-08-01"), " 出差 ", null, 2);

        ReimbursementOutDTO result = service.update(7L, 10L, input);

        assertEquals("研发中心", result.reimburserName());
        assertEquals("出差", result.note());
        assertEquals(3, result.version());
        verify(reimbursementMapper).updateById(entity);
    }

    @Test
    void shouldCreateManualReimbursementWithoutExpenseTransaction() {
        ReimbursementCreateInDTO input = new ReimbursementCreateInDTO(null, "财务部",
                LocalDate.parse("2026-07-27"), null, "差旅报销", 1L, new BigDecimal("120.50"));

        ReimbursementOutDTO result = service.create(7L, input);

        assertNull(result.expenseTransactionId());
        assertEquals(new BigDecimal("120.50"), result.expectedAmount());
        assertEquals("PENDING", result.status());
        verify(bookAccessService).requireWritable(7L, 1L);
        verify(transactionMapper, never()).selectById(any());
        verify(reimbursementMapper).insert(any(ReimbursementEntity.class));
    }

    @Test
    void shouldUpdateAmountForManualReimbursement() {
        ReimbursementEntity entity = reimbursement("PENDING");
        entity.setExpenseTransactionId(null);
        when(reimbursementMapper.selectByIdForUpdate(10L)).thenReturn(entity);
        ReimbursementUpdateInDTO input = new ReimbursementUpdateInDTO("财务部",
                LocalDate.parse("2026-07-27"), null, null, new BigDecimal("99.00"), 2);

        ReimbursementOutDTO result = service.update(7L, 10L, input);

        assertEquals(new BigDecimal("99.00"), result.expectedAmount());
        assertEquals(3, result.version());
        verify(reimbursementMapper).updateById(entity);
    }

    @Test
    void shouldRejectAmountChangeForLinkedReimbursement() {
        ReimbursementEntity entity = reimbursement("PENDING");
        when(reimbursementMapper.selectByIdForUpdate(10L)).thenReturn(entity);
        ReimbursementUpdateInDTO input = new ReimbursementUpdateInDTO("财务部",
                LocalDate.parse("2026-07-27"), null, null, new BigDecimal("99.00"), 2);

        assertThrows(BusinessException.class, () -> service.update(7L, 10L, input));

        verify(reimbursementMapper, never()).updateById(entity);
    }

    @Test
    void shouldReceiveManualReimbursementWithoutOriginalTransaction() {
        ReimbursementEntity entity = reimbursement("PENDING");
        entity.setExpenseTransactionId(null);
        TransactionOutDTO transaction = new TransactionOutDTO(99L, "TXN_99", "request-99",
                1L, 7L, "INCOME", 40L, null, new BigDecimal("88.00"), "CNY",
                Instant.parse("2026-07-27T12:00:00Z"), "报销到账", null,
                "EFFECTIVE", 0, List.of(), List.of());
        when(reimbursementMapper.selectByIdForUpdate(10L)).thenReturn(entity);
        when(transactionService.create(eq(7L), any(TransactionCreateInDTO.class)))
                .thenReturn(transaction);

        service.receive(7L, 10L, new ReimbursementReceiveInDTO(
                30L, 40L, Instant.parse("2026-07-27T12:00:00Z"), null));

        ArgumentCaptor<TransactionCreateInDTO> captor = ArgumentCaptor.forClass(TransactionCreateInDTO.class);
        verify(transactionService).create(eq(7L), captor.capture());
        assertNull(captor.getValue().originalTransactionId());
        assertEquals(new BigDecimal("88.00"), captor.getValue().amount());
    }

    @Test
    void shouldRejectDeletingReceivedReimbursement() {
        ReimbursementEntity entity = reimbursement("REIMBURSED");
        entity.setReimbursementTransactionId(99L);
        when(reimbursementMapper.selectByIdForUpdate(10L)).thenReturn(entity);

        assertThrows(BusinessException.class, () -> service.delete(7L, 10L));

        verify(reimbursementMapper, never()).deleteById(10L);
    }

    @Test
    void shouldRestoreCancelledReimbursementWhenCreatedAgain() {
        TransactionEntity expense = expense();
        ReimbursementEntity entity = reimbursement("CANCELLED");
        when(transactionMapper.selectById(20L)).thenReturn(expense);
        when(reimbursementMapper.selectOne(any(Wrapper.class))).thenReturn(entity);
        ReimbursementCreateInDTO input = new ReimbursementCreateInDTO(20L, "客户单位",
                LocalDate.parse("2026-07-27"), null, null);

        ReimbursementOutDTO result = service.create(7L, input);

        assertEquals("PENDING", result.status());
        assertEquals("客户单位", result.reimburserName());
        assertEquals(3, result.version());
        assertNull(result.expectedDate());
        verify(reimbursementMapper).updateById(entity);
    }

    private ReimbursementEntity reimbursement(String status) {
        ReimbursementEntity entity = new ReimbursementEntity();
        entity.setId(10L);
        entity.setReimbursementNo("RMB_10");
        entity.setBookId(1L);
        entity.setExpenseTransactionId(20L);
        entity.setExpectedAmount(new BigDecimal("88.00"));
        entity.setStatus(status);
        entity.setVersion(2);
        return entity;
    }

    private TransactionEntity expense() {
        TransactionEntity expense = new TransactionEntity();
        expense.setId(20L);
        expense.setBookId(1L);
        expense.setTransactionType("EXPENSE");
        expense.setStatus("EFFECTIVE");
        expense.setAmount(new BigDecimal("88.00"));
        return expense;
    }
}
