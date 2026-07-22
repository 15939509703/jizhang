package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookUpdateInDTO(
        @NotBlank(message = "账本名称不能为空")
        @Size(max = 64, message = "账本名称长度不能超过64个字符") String name,
        @Size(max = 500, message = "账本说明长度不能超过500个字符") String description,
        @Size(max = 512, message = "封面地址长度不能超过512个字符") String coverUrl
) {
}
