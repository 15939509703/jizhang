package com.lhj.jizhang.user.dto;

import java.util.List;

/**
 * 账单年度与月度收支汇总。
 *
 * @param currencyCode 账本币种
 * @param year 当前选中的年份
 * @param years 各自然年的收入和支出
 * @param months 选中年份各自然月的收入和支出
 */
public record BillOverviewOutDTO(
        String currencyCode,
        int year,
        List<StatisticsTrendOutDTO> years,
        List<StatisticsTrendOutDTO> months
) {
}
