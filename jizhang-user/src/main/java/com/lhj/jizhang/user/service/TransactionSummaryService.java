package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.TransactionSummaryOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
public class TransactionSummaryService {
    private final TransactionMapper transactionMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    public TransactionSummaryService(
            TransactionMapper transactionMapper,
            BookMapper bookMapper,
            BookAccessService bookAccessService
    ) {
        this.transactionMapper = transactionMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    public TransactionSummaryOutDTO summarize(Long userId, Long bookId, YearMonth month) {
        bookAccessService.requireMember(userId, bookId);
        BookEntity book = requireBook(bookId);
        ZoneId timezone = ZoneId.of(book.getTimezone());
        YearMonth targetMonth = month == null ? YearMonth.now(timezone) : month;
        LocalDateTime startAt = toUtc(targetMonth.atDay(1).atStartOfDay(timezone));
        LocalDateTime endAt = toUtc(targetMonth.plusMonths(1).atDay(1).atStartOfDay(timezone));
        TransactionSummaryAggregate aggregate = transactionMapper.selectSummary(bookId, startAt, endAt);
        BigDecimal income = valueOrZero(aggregate == null ? null : aggregate.getIncomeAmount());
        BigDecimal expense = valueOrZero(aggregate == null ? null : aggregate.getExpenseAmount());
        return new TransactionSummaryOutDTO(targetMonth.toString(), income, expense,
                income.subtract(expense), book.getCurrencyCode());
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    private LocalDateTime toUtc(java.time.ZonedDateTime dateTime) {
        return LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
