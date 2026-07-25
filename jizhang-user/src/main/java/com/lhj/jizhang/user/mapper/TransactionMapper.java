package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.model.CategoryExpenseAggregate;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionMapper extends BaseMapper<TransactionEntity> {
    @Select("SELECT * FROM fin_transaction WHERE id = #{id} FOR UPDATE")
    TransactionEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT
                COALESCE(SUM(CASE WHEN transaction_type = 'INCOME' THEN amount ELSE 0 END), 0) AS income_amount,
                COALESCE(SUM(CASE WHEN transaction_type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense_amount
            FROM fin_transaction t
            WHERE t.book_id = #{bookId}
              AND t.status = 'EFFECTIVE'
              AND t.happened_at >= #{startAt}
              AND t.happened_at < #{endAt}
              AND NOT EXISTS (
                SELECT 1 FROM fin_reimbursement r
                WHERE (r.expense_transaction_id = t.id AND r.status IN ('PENDING', 'REIMBURSED'))
                   OR (r.reimbursement_transaction_id = t.id AND r.status = 'REIMBURSED')
              )
            """)
    TransactionSummaryAggregate selectSummary(
            @Param("bookId") Long bookId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Select("""
            SELECT
                category_id,
                COALESCE(SUM(amount), 0) AS expense_amount
            FROM fin_transaction t
            WHERE t.book_id = #{bookId}
              AND t.transaction_type = 'EXPENSE'
              AND t.status = 'EFFECTIVE'
              AND t.happened_at >= #{startAt}
              AND t.happened_at < #{endAt}
              AND NOT EXISTS (SELECT 1 FROM fin_reimbursement r
                WHERE r.expense_transaction_id = t.id AND r.status IN ('PENDING', 'REIMBURSED'))
            GROUP BY t.category_id
            """)
    List<CategoryExpenseAggregate> selectExpenseByCategory(
            @Param("bookId") Long bookId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
