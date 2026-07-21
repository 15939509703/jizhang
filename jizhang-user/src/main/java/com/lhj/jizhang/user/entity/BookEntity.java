package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fin_book")
public class BookEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bookNo;
    private Long ownerUserId;
    private String name;
    private String description;
    private String currencyCode;
    private String timezone;
    private Integer status;
    private Integer version;
    private String creator;
    private String modifier;
}
