package com.vagaflow.alpr.dto;

import com.vagaflow.alpr.domain.enums.GateDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GateCameraEventRequest(
        @NotBlank(message = "O ID do patio (lotId) e obrigatorio")
        String lotId,

        @NotNull(message = "A direcao da cancela (ENTRY ou EXIT) e obrigatoria")
        GateDirection direction,

        @NotBlank(message = "A placa lida pelo OCR e obrigatoria")
        String detectedPlate,

        @NotBlank(message = "A cor detectada pela camera e obrigatoria")
        String detectedColor,

        String driverName
) {
}
