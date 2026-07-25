package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
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
        enqueueLevel(userId, book, month, budget, rate, new BigDecimal("0.5000"), "HALF");
        enqueueLevel(userId, book, month, budget, rate, budget.warningRate(), "WARNING");
        enqueueLevel(userId, book, month, budget, rate, BigDecimal.ONE, "OVER");
    }

    private void enqueueLevel(Long userId, BookEntity book, YearMonth month, BudgetOutDTO budget,
                              BigDecimal actual, BigDecimal threshold, String level) {
        if (threshold == null || actual == null || actual.compareTo(threshold) < 0) return;
        subscriptionService.enqueue("BUDGET_" + book.getId() + "_" + month + "_" + level, userId,
                "BUDGET_ALERT", "/pages/budget/budget",
                Map.of("thing1", Map.of("value", crop(book.getName(), 20)),
                        "amount2", Map.of("value", budget.usedAmount().toPlainString()),
                        "thing3", Map.of("value", level)));
    }

    private String crop(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
