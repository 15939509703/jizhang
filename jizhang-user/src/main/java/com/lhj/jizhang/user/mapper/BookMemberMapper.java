package com.lhj.jizhang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BookMemberMapper extends BaseMapper<BookMemberEntity> {
    /** 仅更新当前成员的私有备注，不覆盖成员其他字段。 */
    @Update("UPDATE fin_book_member SET remark_name = #{remark}, modifier = #{actorId} WHERE id = #{memberId} AND status = 1")
    int updateRemark(@Param("memberId") Long memberId, @Param("remark") String remark,
                     @Param("actorId") String actorId);

    /** 按成员、账本和旧版本原子保存完整策略，成员退出或并发修改时不覆盖。 */
    @Update("UPDATE fin_book_member SET role = #{role}, menu_permissions = #{menus}, permissions = #{actions}, "
            + "transaction_scope = #{scope}, policy_version = policy_version + 1, modifier = #{actorId} "
            + "WHERE id = #{memberId} AND book_id = #{bookId} AND status = 1 AND policy_version = #{version}")
    int updatePolicy(@Param("memberId") Long memberId, @Param("bookId") Long bookId,
                     @Param("version") Long version, @Param("role") String role,
                     @Param("menus") String menus, @Param("actions") String actions,
                     @Param("scope") String scope, @Param("actorId") String actorId);

    /** 仅允许停用成员在旧版本匹配时接受新邀请，避免覆盖并发策略。 */
    @Update("UPDATE fin_book_member SET role = #{role}, menu_permissions = #{menus}, permissions = #{actions}, "
            + "transaction_scope = #{scope}, status = 1, invited_by = #{inviterId}, "
            + "joined_time = UTC_TIMESTAMP(), policy_version = policy_version + 1, modifier = #{userId} "
            + "WHERE id = #{memberId} AND book_id = #{bookId} AND status = 0 AND policy_version = #{version} "
            + "AND role <> 'OWNER'")
    int reactivateWithInvitation(@Param("memberId") Long memberId, @Param("bookId") Long bookId,
                                 @Param("version") Long version, @Param("role") String role,
                                 @Param("menus") String menus, @Param("actions") String actions,
                                 @Param("scope") String scope, @Param("inviterId") Long inviterId,
                                 @Param("userId") String userId);

    /** 停用成员时校验策略版本和管理员管理上限。 */
    @Update("UPDATE fin_book_member SET status = 0, policy_version = policy_version + 1, modifier = #{actorId} "
            + "WHERE id = #{memberId} AND book_id = #{bookId} AND status = 1 AND policy_version = #{version} "
            + "AND role <> 'OWNER' AND (#{actorRole} = 'OWNER' OR role <> 'ADMIN')")
    int deactivate(@Param("memberId") Long memberId, @Param("bookId") Long bookId,
                   @Param("version") Long version, @Param("actorRole") String actorRole,
                   @Param("actorId") String actorId);
}
