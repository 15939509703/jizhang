package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BookInvitationCreateInDTO(
        @NotBlank @Pattern(regexp = "ADMIN|MEMBER|VIEWER") String role,
        @NotNull @Min(1) @Max(168) Integer expiresInHours
) { }
