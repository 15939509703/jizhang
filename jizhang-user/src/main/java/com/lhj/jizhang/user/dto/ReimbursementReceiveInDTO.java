package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ReimbursementReceiveInDTO(
        @NotNull Long accountId,
        @NotNull Long categoryId,
        @NotNull Instant happenedAt,
        @Size(max = 1000) String note
) { }
