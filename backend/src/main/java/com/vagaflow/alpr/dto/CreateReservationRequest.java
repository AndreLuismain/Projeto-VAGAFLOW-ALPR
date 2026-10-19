package com.vagaflow.alpr.dto;

import com.vagaflow.alpr.domain.enums.PlateAngle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(
        @NotBlank(message = "O ID do patio (lotId) e obrigatorio")
        String lotId,

        @NotBlank(message = "O nome do motorista e obrigatorio")
        String driverName,

        @NotBlank(message = "A placa do veiculo e obrigatoria")
        @Size(min = 7, max = 8, message = "A placa deve ter 7 caracteres")
        String licensePlate,

        @NotBlank(message = "A cor do veiculo e obrigatoria")
        String vehicleColor,

        PlateAngle photoAngle
) {
}
