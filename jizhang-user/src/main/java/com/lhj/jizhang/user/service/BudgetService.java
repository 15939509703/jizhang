package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.BudgetItemOutDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BudgetService {
    private static final BigDecimal DEFAULT_WARNING_RATE = new BigDecimal("0.8000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final BudgetMapper budgetMapper;
    private final BudgetItemMapper budgetItemMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    public BudgetService(
            BudgetMapper budgetMapper,
            BudgetItemMapper budgetItemMapper,
            CategoryMapper categoryMapper,
            TransactionMapper transactionMapper,
            BookMapper bookMapper,
            BookAccessService bookAccessService
    ) {
        this.budgetMapper = budgetMapper;
        this.budgetItemMapper = budgetItemMapper;
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    public BudgetOutDTO getBudget(Long userId, Long bookId, YearMonth month) {
        bookAccessService.requireMember(userId, bookId);
        BookEntity book = requireBook(bookId);
        YearMonth targetMonth = resolveMonth(book, month);
        BudgetEntity budget = findBudget(bookId, targetMonth);
        return toOutput(book, targetMonth, budget);
    }

    @Transactional
    public BudgetOutDTO saveBudget(Long userId, BudgetSaveInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        BookEntity book = requireBook(input.bookId());
        YearMonth month = YearMonth.parse(input.month());
        validateItems(input);
        BudgetEntity budget = upsertBudget(userId, input);
        replaceItems(userId, budget.getId(), input.items());
        return toOutput(book, month, budget);
    }

    private BudgetEntity upsertBudget(Long userId, BudgetSaveInDTO input) {
        BudgetEntity budget = findBudget(input.bookId(), YearMonth.parse(input.month()));
        if (budget == null) {
            budget = new BudgetEntity();
            budget.setBudgetNo(BusinessIdGenerator.next("BUD_"));
            budget.setBookId(input.bookId());
            budget.setBudgetMonth(input.month());
            budget.setStatus(1);
            budget.setVersion(0);
            budget.setCreator(String.valueOf(userId));
        } else {
            budget.setVersion(budget.getVersion() + 1);
        }
        budget.setTotalLimit(input.totalLimit());
        budget.setWarningRate(input.warningRate());
        budget.setModifier(String.valueOf(userId));
        if (budget.getId() == null) {
            budgetMapper.insert(budget);
        } else {
            budgetMapper.updateById(budget);
        }
        return budget;
    }

    private void replaceItems(Long userId, Long budgetId, List<BudgetItemSaveInDTO> items) {
        budgetItemMapper.delete(Wrappers.<BudgetItemEntity>lambdaQuery()
                .eq(BudgetItemEntity::getBudgetId, budgetId));
        if (items == null) {
            return;
        }
        for (BudgetItemSaveInDTO input : items) {
            BudgetItemEntity item = new BudgetItemEntity();
            item.setBudgetId(budgetId);
            item.setCategoryId(input.categoryId());
            item.setLimitAmount(input.limitAmount());
            item.setWarningRate(input.warningRate());
            item.setCreator(String.valueOf(userId));
            item.setModifier(String.valueOf(userId));
            budgetItemMapper.insert(item);
        }
    }

    private void validateItems(BudgetSaveInDTO input) {
        List<BudgetItemSaveInDTO> items = input.items() == null ? List.of() : input.items();
        Set<Long> categoryIds = items.stream().map(BudgetItemSaveInDTO::categoryId).collect(Collectors.toSet());
        if (categoryIds.size() != items.size()) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "分类预算不能重复");
        }
        Set<Long> validCategoryIds = expenseCategories(input.bookId()).stream()
                .map(CategoryEntity::getId)
                .collect(Collectors.toSet());
        if (!validCategoryIds.containsAll(categoryIds)) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类预算只能选择当前账本的支出分类");
        }
    }

    private BudgetOutDTO toOutput(BookEntity book, YearMonth month, BudgetEntity budget) {
        Map<Long, BudgetItemEntity> itemMap = loadItems(budget);
        Map<Long, BigDecimal> expenseMap = loadExpenseMap(book, month);
        List<BudgetItemOutDTO> items = expenseCategories(book.getId()).stream()
                .map(category -> toItemOutput(category, itemMap.get(category.getId()), expenseMap.get(category.getId())))
                .toList();
        BigDecimal usedAmount = expenseMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLimit = budget == null ? BigDecimal.ZERO : valueOrZero(budget.getTotalLimit());
        BigDecimal warningRate = budget == null ? DEFAULT_WARNING_RATE : budget.getWarningRate();
        return new BudgetOutDTO(
                budget == null ? null : budget.getId(),
                budget == null ? null : budget.getBudgetNo(),
                book.getId(),
                month.toString(),
                totalLimit,
                warningRate,
                usedAmount,
                totalLimit.subtract(usedAmount),
                usageRate(totalLimit, usedAmount),
                usagePercent(totalLimit, usedAmount),
                budget == null ? 1 : budget.getStatus(),
                usageStatus(totalLimit, usedAmount, warningRate),
                book.getCurrencyCode(),
                items
        );
    }

    private BudgetItemOutDTO toItemOutput(
            CategoryEntity category,
            BudgetItemEntity item,
            BigDecimal usedAmount
    ) {
        BigDecimal limitAmount = item == null ? BigDecimal.ZERO : valueOrZero(item.getLimitAmount());
        BigDecimal warningRate = item == null ? DEFAULT_WARNING_RATE : item.getWarningRate();
        BigDecimal used = valueOrZero(usedAmount);
        return new BudgetItemOutDTO(
                item == null ? null : item.getId(),
                category.getId(),
                category.getName(),
                limitAmount,
                warningRate,
                used,
                limitAmount.subtract(used),
                usageRate(limitAmount, used),
                usagePercent(limitAmount, used),
                usageStatus(limitAmount, used, warningRate)
        );
    }

    private Map<Long, BudgetItemEntity> loadItems(BudgetEntity budget) {
        if (budget == null) {
            return Map.of();
        }
        return budgetItemMapper.selectList(Wrappers.<BudgetItemEntity>lambdaQuery()
                        .eq(BudgetItemEntity::getBudgetId, budget.getId()))
                .stream()
                .collect(Collectors.toMap(BudgetItemEntity::getCategoryId, Function.identity()));
    }

    private Map<Long, BigDecimal> loadExpenseMap(BookEntity book, YearMonth month) {
        ZoneId timezone = ZoneId.of(book.getTimezone());
        LocalDateTime startAt = toUtc(month.atDay(1).atStartOfDay(timezone));
        LocalDateTime endAt = toUtc(month.plusMonths(1).atDay(1).atStartOfDay(timezone));
        return transactionMapper.selectExpenseByCategory(book.getId(), startAt, endAt).stream()
                .collect(Collectors.toMap(CategoryExpenseAggregate::getCategoryId,
                        aggregate -> valueOrZero(aggregate.getExpenseAmount()), (left, right) -> right,
                        LinkedHashMap::new));
    }

    private List<CategoryEntity> expenseCategories(Long bookId) {
        return categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .eq(CategoryEntity::getCategoryType, "EXPENSE")
                .eq(CategoryEntity::getHiddenFlag, 0)
                .eq(CategoryEntity::getDeletedFlag, 0)
                .orderByAsc(CategoryEntity::getSortNo, CategoryEntity::getId));
    }

    private BudgetEntity findBudget(Long bookId, YearMonth month) {
        return budgetMapper.selectOne(Wrappers.<BudgetEntity>lambdaQuery()
                .eq(BudgetEntity::getBookId, bookId)
                .eq(BudgetEntity::getBudgetMonth, month.toString())
                .eq(BudgetEntity::getStatus, 1));
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    private YearMonth resolveMonth(BookEntity book, YearMonth month) {
        return month == null ? YearMonth.now(ZoneId.of(book.getTimezone())) : month;
    }

    private LocalDateTime toUtc(java.time.ZonedDateTime dateTime) {
        return LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
    }

    private BigDecimal usageRate(BigDecimal limit, BigDecimal used) {
        BigDecimal normalizedLimit = valueOrZero(limit);
        if (normalizedLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valueOrZero(used).divide(normalizedLimit, 4, RoundingMode.HALF_UP);
    }

    private Integer usagePercent(BigDecimal limit, BigDecimal used) {
        return usageRate(limit, used).multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String usageStatus(BigDecimal limit, BigDecimal used, BigDecimal warningRate) {
        if (valueOrZero(limit).compareTo(BigDecimal.ZERO) <= 0) {
            return "UNSET";
        }
        BigDecimal rate = usageRate(limit, used);
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            return "OVER";
        }
        return rate.compareTo(warningRate) >= 0 ? "WARN" : "OK";
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
