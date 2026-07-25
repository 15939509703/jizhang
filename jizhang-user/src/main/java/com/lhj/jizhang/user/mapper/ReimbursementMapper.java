package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.ReimbursementEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReimbursementMapper extends BaseMapper<ReimbursementEntity> {
    @Select("SELECT * FROM fin_reimbursement WHERE id = #{id} FOR UPDATE")
    ReimbursementEntity selectByIdForUpdate(@Param("id") Long id);
}
