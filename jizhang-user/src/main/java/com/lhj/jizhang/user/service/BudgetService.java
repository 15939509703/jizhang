package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookAction;
import com.lhj.jizhang.user.model.BookMenu;

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
import java.time.LocalDate;
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

    /**
     * 初始化预算服务。
     *
     * @param budgetMapper 数据访问器
     * @param budgetItemMapper 数据访问器
     * @param categoryMapper 数据访问器
     * @param transactionMapper 数据访问器
     * @param bookMapper 数据访问器
     * @param bookAccessService 关联服务
     */
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

    /**
     * 查询预算。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @param month 目标月份
     * @return 预算输出对象
     */
    public BudgetOutDTO getBudget(Long userId, Long bookId, YearMonth month) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.BUDGET);
        BookEntity book = requireBook(bookId);
        YearMonth targetMonth = resolveMonth(book, month);
        BudgetEntity budget = findBudget(bookId, targetMonth);
        BudgetEntity effectiveBudget = budget == null ? findPreviousBudget(bookId, targetMonth) : budget;
        return toOutput(book, targetMonth, budget, effectiveBudget);
    }

    /**
     * 保存预算。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 预算输出对象
     */
    @Transactional
    public BudgetOutDTO saveBudget(Long userId, BudgetSaveInDTO input) {
        bookAccessService.requireAction(userId, input.bookId(), BookAction.BUDGET_MANAGE);
        BookEntity book = requireBook(input.bookId());
        YearMonth month = YearMonth.parse(input.month());
        validateItems(input);
        BudgetEntity budget = upsertBudget(userId, input);
        replaceItems(userId, budget.getId(), input.items());
        return toOutput(book, month, budget, budget);
    }

    /**
     * 创建或更新月度预算。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 预算实体
     */
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

    /**
     * 替换明细。
     *
     * @param userId 用户ID
     * @param budgetId 预算ID
     * @param items 明细集合
     */
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

    /**
     * 校验明细。
     *
     * @param input 请求参数
     */
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

    /**
     * 转换为预算输出对象。
     *
     * @param book 账本实体
     * @param month 目标月份
     * @param budget 预算实体
     * @param effectiveBudget 生效预算实体
     * @return 预算输出对象
     */
    private BudgetOutDTO toOutput(BookEntity book, YearMonth month, BudgetEntity budget,
                                  BudgetEntity effectiveBudget) {
        Map<Long, BudgetItemEntity> itemMap = loadItems(budget);
        Map<Long, BigDecimal> expenseMap = loadExpenseMap(book, month);
        List<BudgetItemOutDTO> items = expenseCategories(book.getId()).stream()
                .map(category -> toItemOutput(category, itemMap.get(category.getId()), expenseMap.get(category.getId())))
                .toList();
        BigDecimal usedAmount = expenseMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLimit = effectiveBudget == null ? BigDecimal.ZERO : valueOrZero(effectiveBudget.getTotalLimit());
        BigDecimal warningRate = effectiveBudget == null ? DEFAULT_WARNING_RATE : effectiveBudget.getWarningRate();
        BudgetForecast forecast = forecast(book, month, totalLimit, usedAmount);
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
                alertLevel(totalLimit, usedAmount, warningRate),
                forecast.daysElapsed,
                forecast.daysRemaining,
                forecast.dailyAvailable,
                forecast.forecastExpense,
                forecast.forecastRemaining,
                forecast.forecastOverBudget,
                book.getCurrencyCode(),
                items
        );
    }

    /**
     * 转换为明细输出对象。
     *
     * @param category 分类实体
     * @param item 当前明细
     * @param usedAmount 已使用金额
     * @return 预算输出对象
     */
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

    /**
     * 加载明细。
     *
     * @param budget 预算实体
     * @return 预算映射
     */
    private Map<Long, BudgetItemEntity> loadItems(BudgetEntity budget) {
        if (budget == null) {
            return Map.of();
        }
        return budgetItemMapper.selectList(Wrappers.<BudgetItemEntity>lambdaQuery()
                        .eq(BudgetItemEntity::getBudgetId, budget.getId()))
                .stream()
                .collect(Collectors.toMap(BudgetItemEntity::getCategoryId, Function.identity()));
    }

    /**
     * 按分类汇总月度支出。
     *
     * @param book 账本实体
     * @param month 目标月份
     * @return 预算映射
     */
    private Map<Long, BigDecimal> loadExpenseMap(BookEntity book, YearMonth month) {
        ZoneId timezone = ZoneId.of(book.getTimezone());
        LocalDateTime startAt = toUtc(month.atDay(1).atStartOfDay(timezone));
        LocalDateTime endAt = toUtc(month.plusMonths(1).atDay(1).atStartOfDay(timezone));
        return transactionMapper.selectExpenseByCategory(book.getId(), startAt, endAt).stream()
                .collect(Collectors.toMap(CategoryExpenseAggregate::getCategoryId,
                        aggregate -> valueOrZero(aggregate.getExpenseAmount()), (left, right) -> right,
                        LinkedHashMap::new));
    }

    /**
     * 查询账本可用的支出分类。
     *
     * @param bookId 账本ID
     * @return 预算列表
     */
    private List<CategoryEntity> expenseCategories(Long bookId) {
        return categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .eq(CategoryEntity::getCategoryType, "EXPENSE")
                .eq(CategoryEntity::getHiddenFlag, 0)
                .eq(CategoryEntity::getDeletedFlag, 0)
                .orderByAsc(CategoryEntity::getSortNo, CategoryEntity::getId));
    }

    /**
     * 查询预算。
     *
     * @param bookId 账本ID
     * @param month 目标月份
     * @return 预算实体
     */
    private BudgetEntity findBudget(Long bookId, YearMonth month) {
        return budgetMapper.selectOne(Wrappers.<BudgetEntity>lambdaQuery()
                .eq(BudgetEntity::getBookId, bookId)
                .eq(BudgetEntity::getBudgetMonth, month.toString())
                .eq(BudgetEntity::getStatus, 1));
    }

    /**
     * 查找当前月份以前最近一次手动保存的预算，作为后续月份的默认总额与预警比例。
     *
     * @param bookId 账本ID
     * @param month 当前预算月份
     * @return 最近的历史预算，不存在时返回null
     */
    private BudgetEntity findPreviousBudget(Long bookId, YearMonth month) {
        return budgetMapper.selectOne(Wrappers.<BudgetEntity>lambdaQuery()
                .eq(BudgetEntity::getBookId, bookId)
                .lt(BudgetEntity::getBudgetMonth, month.toString())
                .eq(BudgetEntity::getStatus, 1)
                .orderByDesc(BudgetEntity::getBudgetMonth)
                .last("LIMIT 1"));
    }

    /**
     * 查询并校验账本存在。
     *
     * @param bookId 账本ID
     * @return 预算实体
     */
    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }

    /**
     * 确定月份。
     *
     * @param book 账本实体
     * @param month 目标月份
     * @return 计算后的日期
     */
    private YearMonth resolveMonth(BookEntity book, YearMonth month) {
        return month == null ? YearMonth.now(ZoneId.of(book.getTimezone())) : month;
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
     * 计算预算使用率。
     *
     * @param limit 分页条数
     * @param used 已使用金额
     * @return 计算后的金额
     */
    private BigDecimal usageRate(BigDecimal limit, BigDecimal used) {
        BigDecimal normalizedLimit = valueOrZero(limit);
        if (normalizedLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valueOrZero(used).divide(normalizedLimit, 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算预算使用百分比。
     *
     * @param limit 分页条数
     * @param used 已使用金额
     * @return 预算使用百分比
     */
    private Integer usagePercent(BigDecimal limit, BigDecimal used) {
        return usageRate(limit, used).multiply(ONE_HUNDRED).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 根据使用率判断预算状态。
     *
     * @param limit 分页条数
     * @param used 已使用金额
     * @param warningRate 预警比例
     * @return 处理后的文本
     */
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

    /**
     * 根据使用率确定预算预警级别。
     *
     * @param limit 分页条数
     * @param used 已使用金额
     * @param warningRate 预警比例
     * @return 处理后的文本
     */
    private String alertLevel(BigDecimal limit, BigDecimal used, BigDecimal warningRate) {
        if (valueOrZero(limit).compareTo(BigDecimal.ZERO) <= 0) {
            return "UNSET";
        }
        BigDecimal rate = usageRate(limit, used);
        if (rate.compareTo(BigDecimal.ONE) >= 0) {
            return "OVER";
        }
        if (rate.compareTo(warningRate) >= 0) {
            return "WARNING";
        }
        return rate.compareTo(new BigDecimal("0.5000")) >= 0 ? "ATTENTION" : "NORMAL";
    }

    /**
     * 预测预算后续用量。
     *
     * @param book 账本实体
     * @param month 目标月份
     * @param totalLimit 预算总额
     * @param usedAmount 已使用金额
     * @return 预算结果
     */
    private BudgetForecast forecast(BookEntity book, YearMonth month, BigDecimal totalLimit, BigDecimal usedAmount) {
        LocalDate today = LocalDate.now(ZoneId.of(book.getTimezone()));
        YearMonth currentMonth = YearMonth.from(today);
        int daysInMonth = month.lengthOfMonth();
        int elapsed = month.isBefore(currentMonth) ? daysInMonth
                : month.isAfter(currentMonth) ? 0 : today.getDayOfMonth();
        int remainingDays = month.isBefore(currentMonth) ? 0
                : month.isAfter(currentMonth) ? daysInMonth : daysInMonth - elapsed + 1;
        BigDecimal remaining = totalLimit.subtract(usedAmount).max(BigDecimal.ZERO);
        BigDecimal dailyAvailable = remainingDays == 0 ? BigDecimal.ZERO
                : remaining.divide(BigDecimal.valueOf(remainingDays), 2, RoundingMode.HALF_UP);
        if (elapsed < 3) {
            return new BudgetForecast(elapsed, remainingDays, dailyAvailable, null, null, null);
        }
        BigDecimal forecastExpense = usedAmount.divide(BigDecimal.valueOf(elapsed), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysInMonth)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal forecastRemaining = totalLimit.subtract(forecastExpense).setScale(2, RoundingMode.HALF_UP);
        return new BudgetForecast(elapsed, remainingDays, dailyAvailable, forecastExpense, forecastRemaining,
                forecastExpense.compareTo(totalLimit) > 0);
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

    private record BudgetForecast(int daysElapsed, int daysRemaining, BigDecimal dailyAvailable,
                                  BigDecimal forecastExpense, BigDecimal forecastRemaining,
                                  Boolean forecastOverBudget) {
    }
}
