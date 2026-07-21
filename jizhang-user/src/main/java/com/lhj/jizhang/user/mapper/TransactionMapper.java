package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.model.TransactionSummaryAggregate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface TransactionMapper extends BaseMapper<TransactionEntity> {
    @Select("SELECT * FROM fin_transaction WHERE id = #{id} FOR UPDATE")
    TransactionEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT
                COALESCE(SUM(CASE WHEN transaction_type = 'INCOME' THEN amount ELSE 0 END), 0) AS income_amount,
                COALESCE(SUM(CASE WHEN transaction_type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense_amount
            FROM fin_transaction
            WHERE book_id = #{bookId}
              AND status = 'EFFECTIVE'
              AND happened_at >= #{startAt}
              AND happened_at < #{endAt}
            """)
    TransactionSummaryAggregate selectSummary(
            @Param("bookId") Long bookId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
