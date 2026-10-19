package com.vagaflow.alpr.dto;

import java.math.BigDecimal;

public record FinancialMetricsDto(
        BigDecimal platformRevenueTotal,
        BigDecimal partnerRevenueTotal,
        long totalOperations
) {
}
