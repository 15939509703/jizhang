package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "当前用户对账本所属人的备注；留空可恢复原昵称")
public record BookRemarkInDTO(
        @Schema(description = "私有备注", example = "妈妈")
        @Size(max = 32)
        String remarkName
) {
}
