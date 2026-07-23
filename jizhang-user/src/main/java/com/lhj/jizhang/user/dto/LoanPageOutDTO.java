package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "LoanPage", description = "借还记录分页结果")
public record LoanPageOutDTO(
        @Schema(description = "借还记录列表")
        List<LoanOutDTO> items,
        @Schema(description = "上一页最后一条记录ID", example = "100")
        Long nextCursor,
        @Schema(description = "是否还有下一页", example = "true")
        boolean hasMore
) {
}
