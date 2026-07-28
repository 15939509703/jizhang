package com.lhj.jizhang.user.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReimbursementStatisticsFilterTest {
    @Test
    void shouldOnlyExcludeAdvanceReimbursementsFromTransactionSummaries() {
        assertAdvanceFilter(TransactionMapper.class, "selectSummary");
        assertAdvanceFilter(TransactionMapper.class, "selectExpenseByCategory");
        assertAdvanceFilter(TransactionMapper.class, "selectStatisticsTransactions");
    }

    @Test
    void shouldOnlyExcludeAdvanceReimbursementsFromDashboardStatistics() {
        assertAdvanceFilter(StatisticsMapper.class, "selectDaily");
        assertAdvanceFilter(StatisticsMapper.class, "selectMonthly");
        assertAdvanceFilter(StatisticsMapper.class, "selectCategoryBreakdown");
        assertAdvanceFilter(StatisticsMapper.class, "selectAccountBreakdown");
    }

    @Test
    void shouldOnlyExcludeAdvanceReimbursementsFromConsumptionReports() {
        assertAdvanceFilter(ConsumptionReportMapper.class, "selectInsights");
    }

    private void assertAdvanceFilter(Class<?> mapperType, String methodName) {
        Method method = Arrays.stream(mapperType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        assertTrue(sql.contains("reimbursement_type = 'ADVANCE'"),
                () -> mapperType.getSimpleName() + "." + methodName + " must preserve reimbursement statistics semantics");
    }
}
