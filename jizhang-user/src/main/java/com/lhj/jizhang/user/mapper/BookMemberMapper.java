package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BookMemberMapper extends BaseMapper<BookMemberEntity> {
    /** 清除旧的成员权限覆盖，让邀请角色的默认权限立即生效。 */
    @Update("UPDATE fin_book_member SET permissions = NULL WHERE id = #{memberId}")
    int clearPermissions(@Param("memberId") Long memberId);

    /** 仅更新当前成员的私有备注，不覆盖成员其他字段。 */
    @Update("UPDATE fin_book_member SET remark_name = #{remark}, modifier = #{actorId} WHERE id = #{memberId} AND status = 1")
    int updateRemark(@Param("memberId") Long memberId, @Param("remark") String remark,
                     @Param("actorId") String actorId);
}
