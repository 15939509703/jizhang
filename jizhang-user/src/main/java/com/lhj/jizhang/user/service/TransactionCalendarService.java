package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.TransactionCalendarDayOutDTO;
import com.lhj.jizhang.user.dto.TransactionCalendarOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringExecutionEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionCalendarService {
    private final TransactionMapper transactionMapper;
    private final RecurringRuleMapper ruleMapper;
    private final RecurringExecutionMapper executionMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    public TransactionCalendarService(TransactionMapper transactionMapper, RecurringRuleMapper ruleMapper,
                                      RecurringExecutionMapper executionMapper, BookMapper bookMapper,
                                      BookAccessService bookAccessService) {
        this.transactionMapper = transactionMapper;
        this.ruleMapper = ruleMapper;
        this.executionMapper = executionMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    public TransactionCalendarOutDTO get(Long userId, Long bookId, YearMonth month) {
        bookAccessService.requireMember(userId, bookId);
        BookEntity book = requireBook(bookId);
        ZoneId zone = ZoneId.of(book.getTimezone());
        YearMonth target = month == null ? YearMonth.now(zone) : month;
        LocalDateTime startAt = toUtc(target.atDay(1).atStartOfDay(zone));
        LocalDateTime endAt = toUtc(target.plusMonths(1).atDay(1).atStartOfDay(zone));
        List<TransactionEntity> transactions = transactionMapper.selectList(
                Wrappers.<TransactionEntity>lambdaQuery()
                        .eq(TransactionEntity::getBookId, bookId)
                        .eq(TransactionEntity::getStatus, "EFFECTIVE")
                        .in(TransactionEntity::getTransactionType, List.of("EXPENSE", "INCOME"))
                        .ge(TransactionEntity::getHappenedAt, startAt)
                        .lt(TransactionEntity::getHappenedAt, endAt)
                        .orderByAsc(TransactionEntity::getHappenedAt, TransactionEntity::getId));
        Map<LocalDate, DayAmounts> days = aggregate(transactions, zone);
        mergePending(days, bookId, target);
        return output(book, target, days);
    }

    private Map<LocalDate, DayAmounts> aggregate(List<TransactionEntity> transactions, ZoneId zone) {
        Map<LocalDate, DayAmounts> days = new LinkedHashMap<>();
        for (TransactionEntity transaction : transactions) {
            LocalDate date = transaction.getHappenedAt().toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate();
            DayAmounts day = days.computeIfAbsent(date, ignored -> new DayAmounts());
            if ("INCOME".equals(transaction.getTransactionType())) {
                day.income = day.income.add(transaction.getAmount());
            } else {
                day.expense = day.expense.add(transaction.getAmount());
            }
            day.count++;
        }
        return days;
    }

    private void mergePending(Map<LocalDate, DayAmounts> days, Long bookId, YearMonth month) {
        List<Long> ruleIds = ruleMapper.selectList(Wrappers.<RecurringRuleEntity>lambdaQuery()
                        .eq(RecurringRuleEntity::getBookId, bookId)
                        .eq(RecurringRuleEntity::getDeletedFlag, 0)).stream()
                .map(RecurringRuleEntity::getId).toList();
        if (ruleIds.isEmpty()) {
            return;
        }
        List<RecurringExecutionEntity> pending = executionMapper.selectList(
                Wrappers.<RecurringExecutionEntity>lambdaQuery()
                        .in(RecurringExecutionEntity::getRuleId, ruleIds)
                        .eq(RecurringExecutionEntity::getExecutionStatus, "PENDING")
                        .ge(RecurringExecutionEntity::getScheduledDate, month.atDay(1))
                        .lt(RecurringExecutionEntity::getScheduledDate, month.plusMonths(1).atDay(1)));
        for (RecurringExecutionEntity execution : pending) {
            days.computeIfAbsent(execution.getScheduledDate(), ignored -> new DayAmounts()).pending++;
        }
    }

    private TransactionCalendarOutDTO output(BookEntity book, YearMonth month, Map<LocalDate, DayAmounts> dayMap) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        int count = 0;
        List<TransactionCalendarDayOutDTO> days = new ArrayList<>();
        ZoneId zone = ZoneId.of(book.getTimezone());
        for (int dayOfMonth = 1; dayOfMonth <= month.lengthOfMonth(); dayOfMonth++) {
            LocalDate date = month.atDay(dayOfMonth);
            DayAmounts day = dayMap.getOrDefault(date, new DayAmounts());
            income = income.add(day.income);
            expense = expense.add(day.expense);
            count += day.count;
            days.add(new TransactionCalendarDayOutDTO(date, day.income, day.expense, day.count, day.pending,
                    date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant()));
        }
        return new TransactionCalendarOutDTO(book.getId(), month.toString(), LocalDate.now(zone), book.getCurrencyCode(),
                income, expense, count, days);
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    private LocalDateTime toUtc(java.time.ZonedDateTime dateTime) {
        return LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
    }

    private static final class DayAmounts {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private int count;
        private int pending;
    }
}
