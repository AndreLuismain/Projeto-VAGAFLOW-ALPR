package com.vagaflow.alpr.dto;

import com.vagaflow.alpr.domain.enums.SpotStatus;

public record SpotDto(
        String code,
        SpotStatus status,
        String plate,
        String color,
        String driver
) {
}
