package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SyncChanges", description = "增量同步变更")
public record SyncChangesOutDTO(
        @Schema(description = "下一次同步游标，ISO-8601格式")
        String cursor,
        @Schema(description = "账本变更")
        List<BookOutDTO> books,
        @Schema(description = "分类变更")
        List<CategoryOutDTO> categories,
        @Schema(description = "账户变更")
        List<AccountOutDTO> accounts,
        @Schema(description = "账单变更")
        List<TransactionOutDTO> transactions,
        @Schema(description = "预算变更")
        List<BudgetSyncOutDTO> budgets,
        @Schema(description = "借还变更")
        List<LoanOutDTO> loans
) {
}
