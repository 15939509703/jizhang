package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.dto.ConsumptionComparisonOutDTO;
import com.lhj.jizhang.user.dto.ConsumptionReportOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.ConsumptionReportMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.ConsumptionInsightAggregate;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class ConsumptionReportService {
    private final TransactionMapper transactionMapper;
    private final ConsumptionReportMapper reportMapper;
    private final BookMapper bookMapper;
    private final BookAccessService accessService;
    private final BudgetService budgetService;

    public ConsumptionReportService(TransactionMapper transactionMapper, ConsumptionReportMapper reportMapper,
                                    BookMapper bookMapper, BookAccessService accessService,
                                    BudgetService budgetService) {
        this.transactionMapper = transactionMapper;
        this.reportMapper = reportMapper;
        this.bookMapper = bookMapper;
        this.accessService = accessService;
        this.budgetService = budgetService;
    }

    public ConsumptionReportOutDTO get(Long userId, Long bookId, YearMonth month) {
        accessService.requireMember(userId, bookId);
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == 0) throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在");
        ZoneId zone = ZoneId.of(book.getTimezone());
        YearMonth target = month == null ? YearMonth.now(zone) : month;
        Summary current = summary(bookId, target, zone);
        Summary previous = summary(bookId, target.minusMonths(1), zone);
        Summary lastYear = summary(bookId, target.minusYears(1), zone);
        Range range = range(target, zone);
        ZoneOffset offset = zone.getRules().getOffset(target.atDay(1).atStartOfDay(zone).toInstant());
        ConsumptionInsightAggregate insights = reportMapper.selectInsights(bookId, range.start, range.end,
                "Z".equals(offset.getId()) ? "+00:00" : offset.getId());
        int days = insights == null || insights.getExpenseDays() == null ? 0 : insights.getExpenseDays();
        BudgetOutDTO budget = budgetService.getBudget(userId, bookId, target);
        return new ConsumptionReportOutDTO(bookId, target.toString(), book.getCurrencyCode(), current.income,
                current.expense, current.income.subtract(current.expense), budget.usageRate(),
                comparison(current.expense, previous.expense), comparison(current.expense, lastYear.expense),
                value(insights == null ? null : insights.getMaxExpense()), days,
                days == 0 ? BigDecimal.ZERO : current.expense.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP),
                insights == null ? null : insights.getTopCategoryId(),
                insights == null ? null : insights.getTopCategoryName(),
                insights == null ? 0 : insights.getTopCategoryCount());
    }

    private Summary summary(Long bookId, YearMonth month, ZoneId zone) {
        Range range = range(month, zone);
        TransactionSummaryAggregate aggregate = transactionMapper.selectSummary(bookId, range.start, range.end);
        return new Summary(value(aggregate == null ? null : aggregate.getIncomeAmount()),
                value(aggregate == null ? null : aggregate.getExpenseAmount()));
    }

    private ConsumptionComparisonOutDTO comparison(BigDecimal current, BigDecimal base) {
        BigDecimal change = current.subtract(base);
        BigDecimal rate = base.signum() == 0 ? null : change.divide(base, 4, RoundingMode.HALF_UP);
        return new ConsumptionComparisonOutDTO(base, change, rate);
    }

    private Range range(YearMonth month, ZoneId zone) {
        ZonedDateTime start = month.atDay(1).atStartOfDay(zone);
        ZonedDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(zone);
        return new Range(LocalDateTime.ofInstant(start.toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(end.toInstant(), ZoneOffset.UTC));
    }

    private BigDecimal value(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
    private record Range(LocalDateTime start, LocalDateTime end) { }
    private record Summary(BigDecimal income, BigDecimal expense) { }
}
