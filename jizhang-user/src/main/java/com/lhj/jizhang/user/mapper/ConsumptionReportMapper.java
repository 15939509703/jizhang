package com.lhj.jizhang.user.mapper;

import com.lhj.jizhang.user.model.ConsumptionInsightAggregate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface ConsumptionReportMapper {
    @Select("""
            SELECT MAX(t.amount) AS max_expense,
                   COUNT(DISTINCT DATE(CONVERT_TZ(t.happened_at, '+00:00', #{timezoneOffset}))) AS expense_days,
                   (SELECT t2.category_id FROM fin_transaction t2
                    WHERE t2.book_id = #{bookId} AND t2.status = 'EFFECTIVE' AND t2.transaction_type = 'EXPENSE'
                      AND t2.happened_at >= #{startAt} AND t2.happened_at < #{endAt}
                      AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r2 WHERE r2.expense_transaction_id = t2.id
                        AND r2.reimbursement_type = 'ADVANCE' AND r2.status IN ('PENDING', 'REIMBURSED'))
                    GROUP BY t2.category_id ORDER BY COUNT(*) DESC, t2.category_id LIMIT 1) AS top_category_id,
                   (SELECT c.name FROM fin_category c WHERE c.id =
                    (SELECT t3.category_id FROM fin_transaction t3
                     WHERE t3.book_id = #{bookId} AND t3.status = 'EFFECTIVE' AND t3.transaction_type = 'EXPENSE'
                       AND t3.happened_at >= #{startAt} AND t3.happened_at < #{endAt}
                       AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r3 WHERE r3.expense_transaction_id = t3.id
                         AND r3.reimbursement_type = 'ADVANCE' AND r3.status IN ('PENDING', 'REIMBURSED'))
                     GROUP BY t3.category_id ORDER BY COUNT(*) DESC, t3.category_id LIMIT 1)) AS top_category_name,
                   (SELECT COUNT(*) FROM fin_transaction t4
                    WHERE t4.book_id = #{bookId} AND t4.status = 'EFFECTIVE' AND t4.transaction_type = 'EXPENSE'
                      AND t4.happened_at >= #{startAt} AND t4.happened_at < #{endAt}
                      AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r4 WHERE r4.expense_transaction_id = t4.id
                        AND r4.reimbursement_type = 'ADVANCE' AND r4.status IN ('PENDING', 'REIMBURSED'))
                      AND t4.category_id = (SELECT t5.category_id FROM fin_transaction t5
                        WHERE t5.book_id = #{bookId} AND t5.status = 'EFFECTIVE' AND t5.transaction_type = 'EXPENSE'
                          AND t5.happened_at >= #{startAt} AND t5.happened_at < #{endAt}
                          AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r5 WHERE r5.expense_transaction_id = t5.id
                            AND r5.reimbursement_type = 'ADVANCE' AND r5.status IN ('PENDING', 'REIMBURSED'))
                        GROUP BY t5.category_id ORDER BY COUNT(*) DESC, t5.category_id LIMIT 1)) AS top_category_count
            FROM fin_transaction t
            WHERE t.book_id = #{bookId} AND t.status = 'EFFECTIVE' AND t.transaction_type = 'EXPENSE'
              AND t.happened_at >= #{startAt} AND t.happened_at < #{endAt}
              AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r WHERE r.expense_transaction_id = t.id
                AND r.reimbursement_type = 'ADVANCE' AND r.status IN ('PENDING', 'REIMBURSED'))
            """)
    ConsumptionInsightAggregate selectInsights(@Param("bookId") Long bookId,
                                                @Param("startAt") LocalDateTime startAt,
                                                @Param("endAt") LocalDateTime endAt,
                                                @Param("timezoneOffset") String timezoneOffset);
}
