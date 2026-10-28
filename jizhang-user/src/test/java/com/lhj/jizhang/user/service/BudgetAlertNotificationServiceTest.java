package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetAlertNotificationServiceTest {
    @Mock private BudgetService budgetService;
    @Mock private BookMapper bookMapper;
    @Mock private WechatSubscriptionService subscriptionService;
    @InjectMocks private BudgetAlertNotificationService service;

    /** 验证预算过半提醒使用模板的五个字段。 */
    @Test
    void shouldMapHalfAlertToBudgetTemplate() {
        when(bookMapper.selectById(1L)).thenReturn(book());
        when(budgetService.getBudget(7L, 1L, YearMonth.of(2026, 10)))
                .thenReturn(budget("600.00", "400.00", "0.6000", 60));

        service.onExpenseCreated(7L, expense());

        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(subscriptionService).enqueue(eq("BUDGET_1_2026-10_HALF"), eq(7L),
                eq("BUDGET_ALERT"), eq("/pages/budget/budget"), data.capture());
        assertEquals(5, data.getValue().size());
        assertTrue(((String) ((Map<?, ?>) data.getValue().get("time1")).get("value"))
                .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
        assertEquals(Map.of("value", "400"), data.getValue().get("amount3"));
        assertEquals(Map.of("value", "家庭账本预算已使用过半"), data.getValue().get("thing4"));
        assertEquals(Map.of("value", "60%"), data.getValue().get("character_string8"));
        assertEquals(Map.of("value", "500"), data.getValue().get("amount6"));
    }

    /** 验证自定义预警线对应的金额阈值与提醒文字。 */
    @Test
    void shouldMapWarningThresholdToBudgetTemplate() {
        when(bookMapper.selectById(1L)).thenReturn(book());
        when(budgetService.getBudget(7L, 1L, YearMonth.of(2026, 10)))
                .thenReturn(budget("850.00", "150.00", "0.8500", 85));

        service.onExpenseCreated(7L, expense());

        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(subscriptionService).enqueue(eq("BUDGET_1_2026-10_WARNING"), eq(7L),
                eq("BUDGET_ALERT"), eq("/pages/budget/budget"), data.capture());
        assertEquals(Map.of("value", "家庭账本预算达到预警线"), data.getValue().get("thing4"));
        assertEquals(Map.of("value", "800"), data.getValue().get("amount6"));
    }

    /** 验证超额提醒将剩余预算归零并使用总预算作为阈值。 */
    @Test
    void shouldMapOverBudgetAlertToBudgetTemplate() {
        when(bookMapper.selectById(1L)).thenReturn(book());
        when(budgetService.getBudget(7L, 1L, YearMonth.of(2026, 10)))
                .thenReturn(budget("1080.00", "-80.00", "1.0800", 108));

        service.onExpenseCreated(7L, expense());

        ArgumentCaptor<Map> data = ArgumentCaptor.forClass(Map.class);
        verify(subscriptionService).enqueue(eq("BUDGET_1_2026-10_OVER"), eq(7L),
                eq("BUDGET_ALERT"), eq("/pages/budget/budget"), data.capture());
        Map<?, ?> payload = data.getValue();
        assertEquals(Map.of("value", "0"), payload.get("amount3"));
        assertEquals(Map.of("value", "家庭账本预算已超限"), payload.get("thing4"));
        assertEquals(Map.of("value", "108%"), payload.get("character_string8"));
        assertEquals(Map.of("value", "1000"), payload.get("amount6"));
    }

    /** 构造含账本时区的测试账本。 */
    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setName("家庭账本");
        book.setTimezone("Asia/Shanghai");
        return book;
    }

    /** 构造用于触发预警的支出账单。 */
    private TransactionEntity expense() {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setBookId(1L);
        transaction.setTransactionType("EXPENSE");
        transaction.setHappenedAt(LocalDateTime.of(2026, 10, 27, 10, 0));
        return transaction;
    }

    /** 构造总预算为一千元的月度预算结果。 */
    private BudgetOutDTO budget(String used, String remaining, String rate, int percent) {
        return new BudgetOutDTO(null, null, 1L, "2026-10", new BigDecimal("1000.00"),
                new BigDecimal("0.8000"), new BigDecimal(used), new BigDecimal(remaining),
                new BigDecimal(rate), percent, 1, "OK", "ATTENTION", null, null, null,
                null, null, null, "CNY", List.of());
    }
}
