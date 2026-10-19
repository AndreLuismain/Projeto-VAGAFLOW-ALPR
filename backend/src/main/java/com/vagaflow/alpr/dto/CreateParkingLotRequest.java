package com.vagaflow.alpr.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateParkingLotRequest(
        @NotBlank(message = "O nome do estacionamento e obrigatorio")
        String name,

        @NotNull(message = "O valor base hora do dono e obrigatorio")
        @Positive(message = "O valor base hora deve ser maior que zero")
        BigDecimal ownerHourlyRate,

        @Min(value = 2, message = "O patio deve ter no minimo 2 vagas")
        @Max(value = 32, message = "O patio deve ter no maximo 32 vagas")
        int spotsCount,

        String distance
) {
}
