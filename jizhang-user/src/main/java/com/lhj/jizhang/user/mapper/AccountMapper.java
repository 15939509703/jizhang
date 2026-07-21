package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.AccountEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AccountMapper extends BaseMapper<AccountEntity> {
    @Select("SELECT * FROM fin_account WHERE id = #{id} FOR UPDATE")
    AccountEntity selectByIdForUpdate(@Param("id") Long id);
}
