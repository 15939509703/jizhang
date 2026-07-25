package com.lhj.jizhang.user.dto;

import java.util.List;

public record AssetTrendOutDTO(
        Long bookId,
        String currencyCode,
        List<AssetTrendPointOutDTO> points
) {
}
