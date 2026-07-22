package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.StatisticsBreakdownOutDTO;
import com.lhj.jizhang.user.dto.StatisticsDashboardOutDTO;
import com.lhj.jizhang.user.dto.StatisticsSummaryOutDTO;
import com.lhj.jizhang.user.dto.StatisticsTrendOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.StatisticsMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.StatisticsBreakdownAggregate;
import com.lhj.jizhang.user.model.StatisticsPeriodAggregate;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.DayOfWeek.MONDAY;

@Service
public class StatisticsService {
    private final StatisticsMapper statisticsMapper;
    private final TransactionMapper transactionMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    public StatisticsService(StatisticsMapper statisticsMapper, TransactionMapper transactionMapper,
                             BookMapper bookMapper, BookAccessService bookAccessService) {
        this.statisticsMapper = statisticsMapper;
        this.transactionMapper = transactionMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    public StatisticsDashboardOutDTO dashboard(Long userId, Long bookId, YearMonth month, Integer year,
                                               LocalDate weekStart) {
        bookAccessService.requireMember(userId, bookId);
        BookEntity book = requireBook(bookId);
        ZoneId zone = ZoneId.of(book.getTimezone());
        YearMonth targetMonth = month == null ? YearMonth.now(zone) : month;
        int targetYear = year == null ? targetMonth.getYear() : validateYear(year);
        LocalDate targetWeekStart = weekStart == null
                ? LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(MONDAY))
                : weekStart;
        LocalDate targetWeekEnd = targetWeekStart.plusDays(6);
        Range weekRange = range(targetWeekStart.atStartOfDay(zone), targetWeekStart.plusDays(7).atStartOfDay(zone));
        Range monthRange = monthRange(targetMonth, zone);
        Range yearRange = yearRange(targetYear, zone);
        Range todayRange = dayRange(LocalDate.now(zone), zone);
        StatisticsSummaryOutDTO weekSummary = summary(bookId, weekRange);
        StatisticsSummaryOutDTO monthSummary = summary(bookId, monthRange);
        StatisticsSummaryOutDTO yearSummary = summary(bookId, yearRange);
        StatisticsSummaryOutDTO todaySummary = summary(bookId, todayRange);
        List<StatisticsBreakdownOutDTO> weeklyExpenseCategories = categories(bookId, "EXPENSE", weekRange);
        List<StatisticsBreakdownOutDTO> weeklyIncomeCategories = categories(bookId, "INCOME", weekRange);
        List<StatisticsBreakdownOutDTO> monthlyExpenseCategories = categories(bookId, "EXPENSE", monthRange);
        List<StatisticsBreakdownOutDTO> monthlyIncomeCategories = categories(bookId, "INCOME", monthRange);
        List<StatisticsBreakdownOutDTO> annualExpenseCategories = categories(bookId, "EXPENSE", yearRange);
        List<StatisticsBreakdownOutDTO> annualIncomeCategories = categories(bookId, "INCOME", yearRange);
        List<StatisticsBreakdownOutDTO> accounts = accounts(bookId, monthRange);
        List<StatisticsTrendOutDTO> weeklyTrend = dailyTrend(bookId, zone, targetWeekStart, 7);
        List<StatisticsTrendOutDTO> monthlyTrend = monthlyTrend(bookId, zone, targetMonth);
        return new StatisticsDashboardOutDTO(targetMonth.toString(), targetWeekStart.toString(),
                targetWeekEnd.toString(), targetYear, book.getCurrencyCode(), weekSummary, monthSummary,
                yearSummary, todaySummary, weeklyTrend, monthlyTrend, dailyTrend(bookId, zone, 7),
                dailyTrend(bookId, zone, 30), weeklyExpenseCategories, weeklyIncomeCategories,
                monthlyExpenseCategories, monthlyIncomeCategories, annualExpenseCategories,
                annualIncomeCategories, monthlyExpenseCategories, monthlyIncomeCategories, accounts,
                annualTrend(bookId, zone, targetYear));
    }

    private StatisticsSummaryOutDTO summary(Long bookId, Range range) {
        TransactionSummaryAggregate aggregate = transactionMapper.selectSummary(bookId, range.startAt(), range.endAt());
        BigDecimal income = valueOrZero(aggregate == null ? null : aggregate.getIncomeAmount());
        BigDecimal expense = valueOrZero(aggregate == null ? null : aggregate.getExpenseAmount());
        return new StatisticsSummaryOutDTO(income, expense, income.subtract(expense));
    }

    private List<StatisticsTrendOutDTO> dailyTrend(Long bookId, ZoneId zone, int days) {
        LocalDate end = LocalDate.now(zone).plusDays(1);
        LocalDate start = end.minusDays(days);
        return dailyTrend(bookId, zone, start, days);
    }

    private List<StatisticsTrendOutDTO> monthlyTrend(Long bookId, ZoneId zone, YearMonth month) {
        return dailyTrend(bookId, zone, month.atDay(1), month.lengthOfMonth());
    }

    private List<StatisticsTrendOutDTO> dailyTrend(Long bookId, ZoneId zone, LocalDate start, int days) {
        LocalDate end = start.plusDays(days);
        Range range = range(start.atStartOfDay(zone), end.atStartOfDay(zone));
        Map<String, StatisticsPeriodAggregate> values = statisticsMapper
                .selectDaily(bookId, range.startAt(), range.endAt(), offset(zone, start))
                .stream().collect(Collectors.toMap(StatisticsPeriodAggregate::getPeriodKey, Function.identity()));
        List<StatisticsTrendOutDTO> result = new ArrayList<>();
        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            StatisticsPeriodAggregate item = values.get(date.toString());
            result.add(new StatisticsTrendOutDTO(date.toString(), income(item), expense(item)));
        }
        return result;
    }

    private List<StatisticsTrendOutDTO> annualTrend(Long bookId, ZoneId zone, int year) {
        ZonedDateTime start = Year.of(year).atDay(1).atStartOfDay(zone);
        ZonedDateTime end = start.plusYears(1);
        Range range = range(start, end);
        Map<String, StatisticsPeriodAggregate> values = statisticsMapper
                .selectMonthly(bookId, range.startAt(), range.endAt(), offset(zone, start.toLocalDate()))
                .stream().collect(Collectors.toMap(StatisticsPeriodAggregate::getPeriodKey, Function.identity()));
        List<StatisticsTrendOutDTO> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            String key = YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            StatisticsPeriodAggregate item = values.get(key);
            result.add(new StatisticsTrendOutDTO(key, income(item), expense(item)));
        }
        return result;
    }

    private List<StatisticsBreakdownOutDTO> categories(Long bookId, String type, Range range) {
        List<StatisticsBreakdownAggregate> rows = statisticsMapper.selectCategoryBreakdown(bookId, type,
                range.startAt(), range.endAt());
        BigDecimal total = rows.stream().map(row -> "INCOME".equals(type)
                ? valueOrZero(row.getIncomeAmount()) : valueOrZero(row.getExpenseAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream().map(row -> {
            BigDecimal amount = "INCOME".equals(type) ? valueOrZero(row.getIncomeAmount())
                    : valueOrZero(row.getExpenseAmount());
            return new StatisticsBreakdownOutDTO(row.getItemId(), row.getItemName(),
                    valueOrZero(row.getIncomeAmount()), valueOrZero(row.getExpenseAmount()), amount,
                    percent(amount, total));
        }).toList();
    }

    private List<StatisticsBreakdownOutDTO> accounts(Long bookId, Range range) {
        List<StatisticsBreakdownAggregate> rows = statisticsMapper.selectAccountBreakdown(bookId,
                range.startAt(), range.endAt());
        BigDecimal total = rows.stream().map(this::totalActivity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream().map(row -> new StatisticsBreakdownOutDTO(row.getItemId(), row.getItemName(),
                valueOrZero(row.getIncomeAmount()), valueOrZero(row.getExpenseAmount()), totalActivity(row),
                percent(totalActivity(row), total))).toList();
    }

    private BigDecimal totalActivity(StatisticsBreakdownAggregate row) {
        return valueOrZero(row.getIncomeAmount()).add(valueOrZero(row.getExpenseAmount()));
    }

    private int percent(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return amount.multiply(new BigDecimal("100")).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal income(StatisticsPeriodAggregate item) {
        return valueOrZero(item == null ? null : item.getIncomeAmount());
    }

    private BigDecimal expense(StatisticsPeriodAggregate item) {
        return valueOrZero(item == null ? null : item.getExpenseAmount());
    }

    private Range monthRange(YearMonth month, ZoneId zone) {
        return range(month.atDay(1).atStartOfDay(zone), month.plusMonths(1).atDay(1).atStartOfDay(zone));
    }

    private Range yearRange(int year, ZoneId zone) {
        ZonedDateTime start = Year.of(year).atDay(1).atStartOfDay(zone);
        return range(start, start.plusYears(1));
    }

    private Range dayRange(LocalDate date, ZoneId zone) {
        return range(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone));
    }

    private Range range(ZonedDateTime start, ZonedDateTime end) {
        return new Range(toUtc(start), toUtc(end));
    }

    private String offset(ZoneId zone, LocalDate date) {
        ZoneOffset offset = zone.getRules().getOffset(date.atStartOfDay(zone).toInstant());
        return "Z".equals(offset.getId()) ? "+00:00" : offset.getId();
    }

    private int validateYear(Integer year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "统计年份必须在2000到2100之间");
        }
        return year;
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    private LocalDateTime toUtc(ZonedDateTime dateTime) {
        return LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Range(LocalDateTime startAt, LocalDateTime endAt) {
    }
}
