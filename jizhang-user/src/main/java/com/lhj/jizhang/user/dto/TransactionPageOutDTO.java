package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TransactionPage", description = "账单游标分页结果")
public record TransactionPageOutDTO(
        @Schema(description = "当前页账单列表")
        List<TransactionOutDTO> items,
        @Schema(description = "下一页游标，无下一页时为空", example = "80")
        Long nextCursor,
        @Schema(description = "是否还有下一页", example = "true")
        boolean hasMore
) {
}
