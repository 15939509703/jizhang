package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.LoanRecordEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LoanRecordMapper extends BaseMapper<LoanRecordEntity> {
    @Select("SELECT * FROM fin_loan_record WHERE id = #{id} FOR UPDATE")
    LoanRecordEntity selectByIdForUpdate(@Param("id") Long id);
}
