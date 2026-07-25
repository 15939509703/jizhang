package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.BudgetItemSaveInDTO;
import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.dto.BudgetSaveInDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BudgetEntity;
import com.lhj.jizhang.user.entity.BudgetItemEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BudgetItemMapper;
import com.lhj.jizhang.user.mapper.BudgetMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.CategoryExpenseAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {
    @Mock
    private BudgetMapper budgetMapper;
    @Mock
    private BudgetItemMapper budgetItemMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookAccessService bookAccessService;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetMapper, budgetItemMapper, categoryMapper,
                transactionMapper, bookMapper, bookAccessService);
    }

    @Test
    void shouldReturnBudgetUsageByBookTimezone() {
        when(bookMapper.selectById(1L)).thenReturn(activeBook());
        when(budgetMapper.selectOne(any(Wrapper.class))).thenReturn(activeBudget());
        when(budgetItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(foodBudgetItem()));
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(foodCategory()));
        when(transactionMapper.selectExpenseByCategory(1L,
                LocalDateTime.of(2026, 6, 30, 16, 0),
                LocalDateTime.of(2026, 7, 31, 16, 0))).thenReturn(List.of(foodExpense()));

        BudgetOutDTO result = service.getBudget(7L, 1L, YearMonth.of(2026, 7));

        verify(bookAccessService).requireMember(7L, 1L);
        assertEquals(new BigDecimal("5000.00"), result.totalLimit());
        assertEquals(new BigDecimal("45.00"), result.usedAmount());
        assertEquals(1, result.usagePercent());
        assertEquals("OK", result.usageStatus());
        assertEquals(new BigDecimal("45.00"), result.items().getFirst().usedAmount());
        assertEquals(90, result.items().getFirst().usagePercent());
        assertEquals("WARN", result.items().getFirst().usageStatus());
    }

    @Test
    void shouldCreateBudgetAndReplaceItems() {
        BudgetSaveInDTO input = saveInput();
        when(bookMapper.selectById(1L)).thenReturn(activeBook());
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(foodCategory()));
        when(budgetMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(budgetItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(transactionMapper.selectExpenseByCategory(any(), any(), any())).thenReturn(List.of());
        doAnswer(invocation -> {
            BudgetEntity budget = invocation.getArgument(0);
            budget.setId(9L);
            return 1;
        }).when(budgetMapper).insert(any(BudgetEntity.class));

        BudgetOutDTO result = service.saveBudget(7L, input);

        verify(bookAccessService).requireWritable(7L, 1L);
        verify(budgetMapper).insert(any(BudgetEntity.class));
        verify(budgetItemMapper).insert(any(BudgetItemEntity.class));
        assertEquals(9L, result.id());
        assertEquals("2026-07", result.month());
    }

    @Test
    void shouldRejectDuplicateCategoryBudgetItems() {
        BudgetSaveInDTO input = new BudgetSaveInDTO(1L, "2026-07", new BigDecimal("5000.00"),
                new BigDecimal("0.8000"), List.of(
                new BudgetItemSaveInDTO(20L, new BigDecimal("500.00"), new BigDecimal("0.8000")),
                new BudgetItemSaveInDTO(20L, new BigDecimal("600.00"), new BigDecimal("0.8000"))));
        when(bookMapper.selectById(1L)).thenReturn(activeBook());

        assertThrows(BusinessException.class, () -> service.saveBudget(7L, input));
    }

    @Test
    void shouldReturnExpectedAlertLevelAtEachThreshold() {
        BudgetEntity budget = activeBudget();
        budget.setTotalLimit(new BigDecimal("100.00"));
        when(bookMapper.selectById(1L)).thenReturn(activeBook());
        when(budgetMapper.selectOne(any(Wrapper.class))).thenReturn(budget);
        when(budgetItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(transactionMapper.selectExpenseByCategory(any(), any(), any())).thenReturn(
                List.of(expense("49.99")), List.of(expense("50.00")),
                List.of(expense("80.00")), List.of(expense("100.00")));

        assertEquals("NORMAL", service.getBudget(7L, 1L, YearMonth.of(2020, 1)).alertLevel());
        assertEquals("ATTENTION", service.getBudget(7L, 1L, YearMonth.of(2020, 1)).alertLevel());
        assertEquals("WARNING", service.getBudget(7L, 1L, YearMonth.of(2020, 1)).alertLevel());
        assertEquals("OVER", service.getBudget(7L, 1L, YearMonth.of(2020, 1)).alertLevel());
    }

    private BudgetSaveInDTO saveInput() {
        return new BudgetSaveInDTO(1L, "2026-07", new BigDecimal("5000.00"),
                new BigDecimal("0.8000"), List.of(
                new BudgetItemSaveInDTO(20L, new BigDecimal("500.00"), new BigDecimal("0.8000"))));
    }

    private BookEntity activeBook() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setTimezone("Asia/Shanghai");
        book.setCurrencyCode("CNY");
        book.setStatus(1);
        return book;
    }

    private BudgetEntity activeBudget() {
        BudgetEntity budget = new BudgetEntity();
        budget.setId(9L);
        budget.setBudgetNo("BUD_1");
        budget.setBookId(1L);
        budget.setBudgetMonth("2026-07");
        budget.setTotalLimit(new BigDecimal("5000.00"));
        budget.setWarningRate(new BigDecimal("0.8000"));
        budget.setStatus(1);
        budget.setVersion(0);
        return budget;
    }

    private BudgetItemEntity foodBudgetItem() {
        BudgetItemEntity item = new BudgetItemEntity();
        item.setId(11L);
        item.setBudgetId(9L);
        item.setCategoryId(20L);
        item.setLimitAmount(new BigDecimal("50.00"));
        item.setWarningRate(new BigDecimal("0.8000"));
        return item;
    }

    private CategoryEntity foodCategory() {
        CategoryEntity category = new CategoryEntity();
        category.setId(20L);
        category.setBookId(1L);
        category.setCategoryType("EXPENSE");
        category.setName("餐饮");
        category.setSortNo(10);
        category.setHiddenFlag(0);
        category.setDeletedFlag(0);
        return category;
    }

    private CategoryExpenseAggregate foodExpense() {
        CategoryExpenseAggregate aggregate = new CategoryExpenseAggregate();
        aggregate.setCategoryId(20L);
        aggregate.setExpenseAmount(new BigDecimal("45.00"));
        return aggregate;
    }

    private CategoryExpenseAggregate expense(String amount) {
        CategoryExpenseAggregate aggregate = new CategoryExpenseAggregate();
        aggregate.setCategoryId(20L);
        aggregate.setExpenseAmount(new BigDecimal(amount));
        return aggregate;
    }
}
