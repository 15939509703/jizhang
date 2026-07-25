package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record BookMemberPermissionsInDTO(@NotNull Map<String, Boolean> permissions) { }
