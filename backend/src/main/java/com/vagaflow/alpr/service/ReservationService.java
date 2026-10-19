package com.vagaflow.alpr.service;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import com.vagaflow.alpr.domain.entity.Reservation;
import com.vagaflow.alpr.domain.entity.Spot;
import com.vagaflow.alpr.domain.entity.Vehicle;
import com.vagaflow.alpr.domain.enums.PlateAngle;
import com.vagaflow.alpr.domain.enums.ReservationStatus;
import com.vagaflow.alpr.domain.enums.SpotStatus;
import com.vagaflow.alpr.dto.CreateReservationRequest;
import com.vagaflow.alpr.dto.NoShowExpiredResponse;
import com.vagaflow.alpr.dto.NoShowExpiredResponse.AutomaticSplitChargePayload;
import com.vagaflow.alpr.dto.ReservationSyncGateResponse;
import com.vagaflow.alpr.dto.ReservationSyncGateResponse.*;
import com.vagaflow.alpr.engine.ParkingTreeIndexService;
import com.vagaflow.alpr.repository.ParkingLotRepository;
import com.vagaflow.alpr.repository.ReservationRepository;
import com.vagaflow.alpr.repository.SpotRepository;
import com.vagaflow.alpr.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servico que gerencia a criacao de reservas temporarias de 15 minutos (TTL = 900s),
 * sincronizacao do payload JSON com a camera ALPR e liquidacao de No-Show (50%).
 */
@Service
public class ReservationService {

    public static final int RESERVATION_TTL_MINUTES = 15;

    private final ParkingLotRepository parkingLotRepository;
    private final SpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final BillingSplitService billingSplitService;
    private final ParkingTreeIndexService treeIndexService;

    public ReservationService(
            ParkingLotRepository parkingLotRepository,
            SpotRepository spotRepository,
            ReservationRepository reservationRepository,
            VehicleRepository vehicleRepository,
            BillingSplitService billingSplitService,
            ParkingTreeIndexService treeIndexService) {
        this.parkingLotRepository = parkingLotRepository;
        this.spotRepository = spotRepository;
        this.reservationRepository = reservationRepository;
        this.vehicleRepository = vehicleRepository;
        this.billingSplitService = billingSplitService;
        this.treeIndexService = treeIndexService;
    }

    /**
     * Aloca a primeira vaga AVAILABLE como RESERVED por 15 minutos, persiste a reserva,
     * indexa o veiculo na Arvore de Prefixos por Cor + Placa e retorna o JSON de sincronizacao.
     */
    @Transactional
    public ReservationSyncGateResponse createAndSyncGate(CreateReservationRequest request) {
        ParkingLot lot = parkingLotRepository.findById(request.lotId())
                .orElseThrow(() -> new IllegalArgumentException("Patio nao encontrado: " + request.lotId()));

        Spot freeSpot = lot.getSpots().stream()
                .filter(s -> s.getStatus() == SpotStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sem vagas disponiveis no patio " + lot.getName()));

        String cleanPlate = request.licensePlate().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String cleanColor = request.vehicleColor().trim().toUpperCase();
        String driverName = request.driverName().trim();
        PlateAngle angle = request.photoAngle() != null ? request.photoAngle() : PlateAngle.TRASEIRA;

        freeSpot.allocateReserved(cleanPlate, cleanColor, driverName);
        spotRepository.save(freeSpot);

        Vehicle vehicle = new Vehicle(cleanPlate, driverName, cleanColor, angle);
        vehicleRepository.save(vehicle);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(RESERVATION_TTL_MINUTES));
        String reservationId = "RES-" + ThreadLocalRandom.current().nextInt(100000, 999999);

        BigDecimal totalHourly = billingSplitService.calculateTotalHourlyPrice(lot.getOwnerHourlyRate());
        BillingSplitService.SplitBreakdown noShowSplit = billingSplitService.calculateNoShowSplit(lot.getOwnerHourlyRate());

        Reservation reservation = new Reservation(
                reservationId,
                lot.getId(),
                lot.getName(),
                freeSpot.getCode(),
                driverName,
                cleanPlate,
                cleanColor,
                angle,
                lot.getOwnerHourlyRate(),
                totalHourly,
                now,
                expiresAt
        );
        reservationRepository.save(reservation);

        // Atualiza a Arvore de Busca da Camera imediatamente
        treeIndexService.rebuildLotIndex(lot);

        return new ReservationSyncGateResponse(
                "RESERVATION_CREATED_LOCK_SPOT",
                reservationId,
                now.toString(),
                expiresAt.toString(),
                RESERVATION_TTL_MINUTES,
                RESERVATION_TTL_MINUTES * 60L,
                lot.getName(),
                ReservationStatus.RESERVED.name(),
                new CustomerVehiclePayload(
                        driverName,
                        cleanPlate,
                        cleanColor,
                        new PlatePhotoReferencePayload(
                                angle.name(),
                                vehicle.getPhotoUrl(),
                                true
                        )
                ),
                new ParkingLotSyncPayload(
                        lot.getId(),
                        freeSpot.getCode(),
                        SpotStatus.RESERVED.name(),
                        true,
                        new PricingRulePayload(
                                totalHourly,
                                lot.getOwnerHourlyRate(),
                                BillingSplitService.PLATFORM_FIXED_FEE_BRL,
                                noShowSplit.chargedFromCustomerBRL(),
                                noShowSplit.transferredToParkingOwnerBRL(),
                                noShowSplit.transferredToPlatformBRL()
                        )
                )
        );
    }

    /**
     * Dispara a expiracao de 15 minutos (No-Show), libera a vaga de volta para AVAILABLE,
     * remove o veiculo da Arvore de Busca do patio e executa a cobranca automatica de 50%.
     */
    @Transactional
    public NoShowExpiredResponse triggerNoShowPenalty(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva nao encontrada: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Apenas reservas no estado RESERVED podem sofrer expiracao de No-Show.");
        }

        reservation.markExpiredNoShow();
        reservationRepository.save(reservation);

        ParkingLot lot = parkingLotRepository.findById(reservation.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("Patio nao encontrado: " + reservation.getLotId()));

        lot.getSpots().stream()
                .filter(s -> s.getCode().equals(reservation.getSpotCode()))
                .findFirst()
                .ifPresent(spot -> {
                    spot.release();
                    spotRepository.save(spot);
                });

        // Remove o veiculo da Arvore do patio reconstruindo o indice
        treeIndexService.rebuildLotIndex(lot);

        BillingSplitService.SplitBreakdown split = billingSplitService.calculateNoShowSplit(reservation.getOwnerHourlyRate());
        billingSplitService.recordNoShowTransaction(
                reservation.getLotId(),
                reservation.getSpotCode(),
                reservation.getLicensePlate(),
                reservation.getOwnerHourlyRate()
        );

        return new NoShowExpiredResponse(
                "RESERVATION_EXPIRED_NO_SHOW_15MIN",
                reservation.getReservationId(),
                reservation.getLicensePlate(),
                reservation.getSpotCode(),
                new AutomaticSplitChargePayload(
                        split.chargedFromCustomerBRL(),
                        split.transferredToParkingOwnerBRL(),
                        split.transferredToPlatformBRL(),
                        "50% do valor mínimo de 1h (R$ 1,00 fixo da plataforma preservado)"
                )
        );
    }
}
