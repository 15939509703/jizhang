package com.lhj.jizhang.user.mapper;

import com.lhj.jizhang.user.model.StatisticsBreakdownAggregate;
import com.lhj.jizhang.user.model.StatisticsPeriodAggregate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface StatisticsMapper {
    @Select("""
            SELECT DATE_FORMAT(CONVERT_TZ(happened_at, '+00:00', #{timezoneOffset}), '%Y-%m-%d') AS period_key,
                   COALESCE(SUM(CASE WHEN transaction_type = 'INCOME' THEN amount ELSE 0 END), 0) AS income_amount,
                   COALESCE(SUM(CASE WHEN transaction_type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense_amount
            FROM fin_transaction t
            WHERE t.book_id = #{bookId} AND t.status = 'EFFECTIVE'
              AND t.happened_at >= #{startAt} AND t.happened_at < #{endAt}
              AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r
                WHERE r.reimbursement_type = 'ADVANCE'
                  AND ((r.expense_transaction_id = t.id AND r.status IN ('PENDING', 'REIMBURSED'))
                   OR (r.reimbursement_transaction_id = t.id AND r.status = 'REIMBURSED')))
            GROUP BY period_key ORDER BY period_key
            """)
    List<StatisticsPeriodAggregate> selectDaily(
            @Param("bookId") Long bookId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("timezoneOffset") String timezoneOffset
    );

    @Select("""
            SELECT DATE_FORMAT(CONVERT_TZ(happened_at, '+00:00', #{timezoneOffset}), '%Y-%m') AS period_key,
                   COALESCE(SUM(CASE WHEN transaction_type = 'INCOME' THEN amount ELSE 0 END), 0) AS income_amount,
                   COALESCE(SUM(CASE WHEN transaction_type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense_amount
            FROM fin_transaction t
            WHERE t.book_id = #{bookId} AND t.status = 'EFFECTIVE'
              AND t.happened_at >= #{startAt} AND t.happened_at < #{endAt}
              AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r
                WHERE r.reimbursement_type = 'ADVANCE'
                  AND ((r.expense_transaction_id = t.id AND r.status IN ('PENDING', 'REIMBURSED'))
                   OR (r.reimbursement_transaction_id = t.id AND r.status = 'REIMBURSED')))
            GROUP BY period_key ORDER BY period_key
            """)
    List<StatisticsPeriodAggregate> selectMonthly(
            @Param("bookId") Long bookId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("timezoneOffset") String timezoneOffset
    );

    @Select("""
            SELECT c.id AS item_id, c.name AS item_name,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS income_amount,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS expense_amount
            FROM fin_transaction t
            JOIN fin_category c ON c.id = t.category_id
            WHERE t.book_id = #{bookId} AND t.status = 'EFFECTIVE'
              AND t.transaction_type = #{type}
              AND t.happened_at >= #{startAt} AND t.happened_at < #{endAt}
              AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r
                WHERE r.reimbursement_type = 'ADVANCE'
                  AND ((r.expense_transaction_id = t.id AND r.status IN ('PENDING', 'REIMBURSED'))
                   OR (r.reimbursement_transaction_id = t.id AND r.status = 'REIMBURSED')))
            GROUP BY c.id, c.name ORDER BY SUM(t.amount) DESC, c.id
            """)
    List<StatisticsBreakdownAggregate> selectCategoryBreakdown(
            @Param("bookId") Long bookId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Select("""
            SELECT a.id AS item_id, a.name AS item_name,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS income_amount,
                   COALESCE(SUM(CASE WHEN t.transaction_type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS expense_amount
            FROM fin_transaction t
            JOIN fin_account_entry e ON e.transaction_id = t.id
            JOIN fin_account a ON a.id = e.account_id
            WHERE t.book_id = #{bookId} AND t.status = 'EFFECTIVE'
              AND t.transaction_type IN ('INCOME', 'EXPENSE')
              AND e.entry_type <> 'REVERSAL'
              AND t.happened_at >= #{startAt} AND t.happened_at < #{endAt}
              AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r
                WHERE r.reimbursement_type = 'ADVANCE'
                  AND ((r.expense_transaction_id = t.id AND r.status IN ('PENDING', 'REIMBURSED'))
                   OR (r.reimbursement_transaction_id = t.id AND r.status = 'REIMBURSED')))
            GROUP BY a.id, a.name ORDER BY SUM(t.amount) DESC, a.id
            """)
    List<StatisticsBreakdownAggregate> selectAccountBreakdown(
            @Param("bookId") Long bookId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
