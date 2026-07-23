package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_category")
public class CategoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryNo;
    private String scopeType;
    private Long bookId;
    private String categoryType;
    private Long parentId;
    private String name;
    private String icon;
    private String color;
    private Integer sortNo;
    private Integer systemFlag;
    private Integer hiddenFlag;
    private Integer deletedFlag;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
