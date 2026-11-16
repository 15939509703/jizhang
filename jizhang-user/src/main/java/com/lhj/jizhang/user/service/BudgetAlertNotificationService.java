package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.model.BookMenu;
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
    private final BookAccessService accessService;

    /**
     * 初始化预算提醒服务。
     *
     * @param budgetService 关联服务
     * @param bookMapper 数据访问器
     * @param subscriptionService 关联服务
     * @param accessService 关联服务
     */
    public BudgetAlertNotificationService(BudgetService budgetService, BookMapper bookMapper,
                                          WechatSubscriptionService subscriptionService,
                                          BookAccessService accessService) {
        this.budgetService = budgetService; this.bookMapper = bookMapper; this.subscriptionService = subscriptionService;
        this.accessService = accessService;
    }

    /** 没有预算可读权限时跳过可选提醒，不影响已授权的记账事务。 */
    public void onExpenseCreated(Long userId, TransactionEntity transaction) {
        if (!"EXPENSE".equals(transaction.getTransactionType())) return;
        try {
            accessService.requireMenu(userId, transaction.getBookId(), BookMenu.BUDGET);
        } catch (BusinessException exception) {
            return;
        }
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

    /**
     * 按预算使用级别写入预警通知。
     *
     * @param userId 用户ID
     * @param book 账本实体
     * @param month 目标月份
     * @param budget 预算实体
     * @param actual 实际使用金额
     * @param threshold 预警阈值
     * @param level 预警级别
     * @param alertTime 提醒时间
     */
    private void enqueueLevel(Long userId, BookEntity book, YearMonth month, BudgetOutDTO budget,
                              BigDecimal actual, BigDecimal threshold, String level, String alertTime) {
        if (threshold == null || actual == null || actual.compareTo(threshold) < 0) return;
        String reminder = switch (level) {
            case "HALF" -> "预算已使用过半";
            case "OVER" -> "预算已超限";
            default -> "预算达到预警线";
        };
        subscriptionService.enqueue("BUDGET_" + book.getId() + "_" + month + "_" + level, userId, book.getId(),
                "BUDGET_ALERT", "/pages/budget/budget",
                Map.of("time1", Map.of("value", alertTime),
                        "amount3", Map.of("value", budget.remainingAmount().max(BigDecimal.ZERO)
                                .stripTrailingZeros().toPlainString()),
                        "thing4", Map.of("value", crop(book.getName(), 8) + reminder),
                        "character_string8", Map.of("value", budget.usagePercent() + "%"),
                        "amount6", Map.of("value", budget.totalLimit().multiply(threshold)
                                .stripTrailingZeros().toPlainString())));
    }

    /**
     * 截断超出长度限制的通知文本。
     *
     * @param value 原始值
     * @param max 最大长度
     * @return 处理后的文本
     */
    private String crop(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
