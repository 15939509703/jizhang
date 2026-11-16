package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.TransactionSummaryOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import com.lhj.jizhang.user.model.TransactionScope;
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

    /**
     * 初始化账单汇总服务。
     *
     * @param transactionMapper 数据访问器
     * @param bookMapper 数据访问器
     * @param bookAccessService 关联服务
     */
    public TransactionSummaryService(
            TransactionMapper transactionMapper,
            BookMapper bookMapper,
            BookAccessService bookAccessService
    ) {
        this.transactionMapper = transactionMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 汇总指定月份的账单收支。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param month 目标月份
     * @return 账单汇总输出对象
     */
    public TransactionSummaryOutDTO summarize(Long userId, Long bookId, YearMonth month) {
        TransactionScope scope = bookAccessService.requireTransactionScope(userId, bookId);
        BookEntity book = requireBook(bookId);
        ZoneId timezone = ZoneId.of(book.getTimezone());
        YearMonth targetMonth = month == null ? YearMonth.now(timezone) : month;
        LocalDateTime startAt = toUtc(targetMonth.atDay(1).atStartOfDay(timezone));
        LocalDateTime endAt = toUtc(targetMonth.plusMonths(1).atDay(1).atStartOfDay(timezone));
        TransactionSummaryAggregate aggregate = transactionMapper.selectSummary(bookId, startAt, endAt,
                scope == TransactionScope.OWN_CREATED ? userId : null);
        BigDecimal income = valueOrZero(aggregate == null ? null : aggregate.getIncomeAmount());
        BigDecimal expense = valueOrZero(aggregate == null ? null : aggregate.getExpenseAmount());
        return new TransactionSummaryOutDTO(targetMonth.toString(), income, expense,
                income.subtract(expense), book.getCurrencyCode());
    }

    /**
     * 查询并校验账本存在。
     *
     * @param bookId 账本ID
     * @return 账单汇总实体
     */
    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    /**
     * 将时间转换为 UTC 时间。
     *
     * @param dateTime 待转换时间
     * @return 转换后的时间
     */
    private LocalDateTime toUtc(java.time.ZonedDateTime dateTime) {
        return LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
    }

    /**
     * 将空金额转换为零。
     *
     * @param value 原始值
     * @return 计算后的金额
     */
    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
