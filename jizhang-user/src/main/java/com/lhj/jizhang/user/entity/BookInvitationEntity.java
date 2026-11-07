package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_book_invitation")
public class BookInvitationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String invitationNo;
    private Long bookId;
    private Long inviterUserId;
    private String defaultRole;
    /** 邀请创建时冻结的菜单权限。 */
    private String menuPermissions;
    /** 邀请创建时冻结的操作权限。 */
    private String actionPermissions;
    /** 邀请创建时冻结的账单范围。 */
    private String transactionScope;
    private String tokenHash;
    private LocalDateTime expiredTime;
    private String status;
    private Long acceptedUserId;
    private LocalDateTime acceptedTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
