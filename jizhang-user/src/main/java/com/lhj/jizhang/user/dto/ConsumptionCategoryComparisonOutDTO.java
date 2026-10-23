package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;

/**
 * 分类支出相对上月的对比结果。
 *
 * @param categoryId 分类ID
 * @param categoryName 分类名称
 * @param expenseAmount 本月支出金额
 * @param monthOverMonth 上月基准金额、差额和变化率
 */
public record ConsumptionCategoryComparisonOutDTO(
        Long categoryId, String categoryName, BigDecimal expenseAmount,
        ConsumptionComparisonOutDTO monthOverMonth
) { }
