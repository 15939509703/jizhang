package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.SavingsGoalEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SavingsGoalMapper extends BaseMapper<SavingsGoalEntity> {
    @Select("SELECT * FROM fin_savings_goal WHERE id = #{id} FOR UPDATE")
    SavingsGoalEntity selectByIdForUpdate(@Param("id") Long id);
}
