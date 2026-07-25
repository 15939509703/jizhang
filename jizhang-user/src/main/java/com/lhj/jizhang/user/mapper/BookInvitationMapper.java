package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.BookInvitationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BookInvitationMapper extends BaseMapper<BookInvitationEntity> {
    @Select("SELECT * FROM fin_book_invitation WHERE token_hash = #{hash} FOR UPDATE")
    BookInvitationEntity selectByHashForUpdate(@Param("hash") String hash);
}
