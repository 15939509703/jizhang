package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RecurringRuleMapper extends BaseMapper<RecurringRuleEntity> {
    @Select("SELECT * FROM fin_recurring_rule WHERE id = #{id} FOR UPDATE")
    RecurringRuleEntity selectByIdForUpdate(@Param("id") Long id);
}
