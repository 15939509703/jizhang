package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.TransactionSummaryOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionSummaryServiceTest {
    @Test
    void shouldSummarizeMonthUsingBookTimezone() {
        TransactionMapper transactionMapper = mock(TransactionMapper.class);
        BookMapper bookMapper = mock(BookMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        BookEntity book = activeBook();
        TransactionSummaryAggregate aggregate = aggregate("12000.00", "3456.78");
        LocalDateTime startAt = LocalDateTime.of(2026, 6, 30, 16, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 7, 31, 16, 0);
        when(bookMapper.selectById(1L)).thenReturn(book);
        when(transactionMapper.selectSummary(1L, startAt, endAt)).thenReturn(aggregate);
        TransactionSummaryService service = new TransactionSummaryService(
                transactionMapper, bookMapper, bookAccessService);

        TransactionSummaryOutDTO result = service.summarize(7L, 1L, YearMonth.of(2026, 7));

        verify(bookAccessService).requireMember(7L, 1L);
        assertEquals("2026-07", result.month());
        assertEquals(new BigDecimal("12000.00"), result.income());
        assertEquals(new BigDecimal("3456.78"), result.expense());
        assertEquals(new BigDecimal("8543.22"), result.balance());
        assertEquals("CNY", result.currencyCode());
    }

    private BookEntity activeBook() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setTimezone("Asia/Shanghai");
        book.setCurrencyCode("CNY");
        book.setStatus(1);
        return book;
    }

    private TransactionSummaryAggregate aggregate(String income, String expense) {
        TransactionSummaryAggregate aggregate = new TransactionSummaryAggregate();
        aggregate.setIncomeAmount(new BigDecimal(income));
        aggregate.setExpenseAmount(new BigDecimal(expense));
        return aggregate;
    }
}
