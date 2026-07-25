package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.RecurringExecutionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

public interface RecurringExecutionMapper extends BaseMapper<RecurringExecutionEntity> {
    @Select("SELECT * FROM fin_recurring_execution WHERE id = #{id} FOR UPDATE")
    RecurringExecutionEntity selectByIdForUpdate(@Param("id") Long id);

    @Insert("""
            INSERT IGNORE INTO fin_recurring_execution
                (rule_id, scheduled_date, execution_status, creator, modifier)
            VALUES
                (#{ruleId}, #{scheduledDate}, #{executionStatus}, #{creator}, #{modifier})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(RecurringExecutionEntity execution);
}
