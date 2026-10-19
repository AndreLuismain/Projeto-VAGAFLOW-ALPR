package com.vagaflow.alpr.controller;

import com.vagaflow.alpr.dto.CreateReservationRequest;
import com.vagaflow.alpr.dto.NoShowExpiredResponse;
import com.vagaflow.alpr.dto.ReservationSyncGateResponse;
import com.vagaflow.alpr.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints REST para reserva temporaria de 15 minutos, sincronizacao JSON
 * com a Arvore da camera ALPR (sync-gate) e disparo de expiracao No-Show (multa de 50%).
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/sync-gate")
    public ResponseEntity<ReservationSyncGateResponse> createReservationAndSyncGate(
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationSyncGateResponse response = reservationService.createAndSyncGate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{reservationId}/trigger-no-show")
    public ResponseEntity<NoShowExpiredResponse> triggerNoShow(@PathVariable String reservationId) {
        NoShowExpiredResponse response = reservationService.triggerNoShowPenalty(reservationId);
        return ResponseEntity.ok(response);
    }
}
