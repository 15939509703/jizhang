package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssetSummaryOutDTO(
        Long bookId,
        String currencyCode,
        BigDecimal totalAssets,
        BigDecimal totalCreditLimit,
        BigDecimal totalLiabilities,
        BigDecimal netAssets,
        List<AssetAccountOutDTO> assetAccounts,
        List<AssetAccountOutDTO> liabilityAccounts
) {
}
