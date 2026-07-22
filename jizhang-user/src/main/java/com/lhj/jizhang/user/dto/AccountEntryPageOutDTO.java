package com.lhj.jizhang.user.dto;

import java.util.List;

public record AccountEntryPageOutDTO(
        List<AccountEntryOutDTO> items,
        Long nextCursor,
        boolean hasMore
) {
}
