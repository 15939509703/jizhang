package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.SavingsContributionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface SavingsContributionMapper extends BaseMapper<SavingsContributionEntity> {
    @Select("SELECT COALESCE(SUM(amount), 0) FROM fin_savings_contribution WHERE goal_id = #{goalId} AND status = 'EFFECTIVE'")
    BigDecimal sumEffective(@Param("goalId") Long goalId);
}
