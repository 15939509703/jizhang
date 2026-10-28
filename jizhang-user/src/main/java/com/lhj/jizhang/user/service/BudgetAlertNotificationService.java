package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class BudgetAlertNotificationService {
    private final BudgetService budgetService;
    private final BookMapper bookMapper;
    private final WechatSubscriptionService subscriptionService;

    public BudgetAlertNotificationService(BudgetService budgetService, BookMapper bookMapper,
                                          WechatSubscriptionService subscriptionService) {
        this.budgetService = budgetService; this.bookMapper = bookMapper; this.subscriptionService = subscriptionService;
    }

    public void onExpenseCreated(Long userId, TransactionEntity transaction) {
        if (!"EXPENSE".equals(transaction.getTransactionType())) return;
        BookEntity book = bookMapper.selectById(transaction.getBookId());
        YearMonth month = YearMonth.from(transaction.getHappenedAt().atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.of(book.getTimezone())));
        BudgetOutDTO budget = budgetService.getBudget(userId, transaction.getBookId(), month);
        if (budget.totalLimit() == null || budget.totalLimit().signum() <= 0) return;
        BigDecimal rate = budget.usageRate();
        String alertTime = LocalDateTime.now(ZoneId.of(book.getTimezone()))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        enqueueLevel(userId, book, month, budget, rate, new BigDecimal("0.5000"), "HALF", alertTime);
        enqueueLevel(userId, book, month, budget, rate, budget.warningRate(), "WARNING", alertTime);
        enqueueLevel(userId, book, month, budget, rate, BigDecimal.ONE, "OVER", alertTime);
    }

    private void enqueueLevel(Long userId, BookEntity book, YearMonth month, BudgetOutDTO budget,
                              BigDecimal actual, BigDecimal threshold, String level, String alertTime) {
        if (threshold == null || actual == null || actual.compareTo(threshold) < 0) return;
        String reminder = switch (level) {
            case "HALF" -> "预算已使用过半";
            case "OVER" -> "预算已超限";
            default -> "预算达到预警线";
        };
        subscriptionService.enqueue("BUDGET_" + book.getId() + "_" + month + "_" + level, userId,
                "BUDGET_ALERT", "/pages/budget/budget",
                Map.of("time1", Map.of("value", alertTime),
                        "amount3", Map.of("value", budget.remainingAmount().max(BigDecimal.ZERO)
                                .stripTrailingZeros().toPlainString()),
                        "thing4", Map.of("value", crop(book.getName(), 8) + reminder),
                        "character_string8", Map.of("value", budget.usagePercent() + "%"),
                        "amount6", Map.of("value", budget.totalLimit().multiply(threshold)
                                .stripTrailingZeros().toPlainString())));
    }

    private String crop(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
