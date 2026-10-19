package com.vagaflow.alpr.service;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import com.vagaflow.alpr.domain.entity.Spot;
import com.vagaflow.alpr.domain.enums.GateDirection;
import com.vagaflow.alpr.domain.enums.ReservationStatus;
import com.vagaflow.alpr.domain.enums.SpotStatus;
import com.vagaflow.alpr.dto.GateCameraEventRequest;
import com.vagaflow.alpr.dto.GateCameraEventResponse;
import com.vagaflow.alpr.dto.GateCameraEventResponse.BillingSplitPayload;
import com.vagaflow.alpr.engine.ParkingTreeIndexService;
import com.vagaflow.alpr.engine.ParkingTreeIndexService.TreeMatchResult;
import com.vagaflow.alpr.repository.ParkingLotRepository;
import com.vagaflow.alpr.repository.ReservationRepository;
import com.vagaflow.alpr.repository.SpotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servico que processa eventos opticos da camera ALPR na cancela (ENTRY e EXIT)
 * utilizando a busca em Arvore (Cor -> Trie de 7 caracteres da Placa).
 */
@Service
public class GateAccessService {

    private final ParkingLotRepository parkingLotRepository;
    private final SpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingTreeIndexService treeIndexService;
    private final BillingSplitService billingSplitService;

    public GateAccessService(
            ParkingLotRepository parkingLotRepository,
            SpotRepository spotRepository,
            ReservationRepository reservationRepository,
            ParkingTreeIndexService treeIndexService,
            BillingSplitService billingSplitService) {
        this.parkingLotRepository = parkingLotRepository;
        this.spotRepository = spotRepository;
        this.reservationRepository = reservationRepository;
        this.treeIndexService = treeIndexService;
        this.billingSplitService = billingSplitService;
    }

    @Transactional
    public GateCameraEventResponse processGateEvent(GateCameraEventRequest request) {
        ParkingLot lot = parkingLotRepository.findById(request.lotId())
                .orElseThrow(() -> new IllegalArgumentException("Patio nao encontrado: " + request.lotId()));

        String detectedPlate = request.detectedPlate().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String detectedColor = request.detectedColor().trim().toUpperCase();

        treeIndexService.rebuildLotIndex(lot);
        Optional<TreeMatchResult> matchOpt = treeIndexService.search(lot.getId(), detectedColor, detectedPlate);

        if (request.direction() == GateDirection.ENTRY) {
            return handleEntry(lot, detectedPlate, detectedColor, request.driverName(), matchOpt);
        } else {
            return handleExit(lot, detectedPlate, matchOpt);
        }
    }

    private GateCameraEventResponse handleEntry(
            ParkingLot lot,
            String detectedPlate,
            String detectedColor,
            String driverName,
            Optional<TreeMatchResult> matchOpt) {

        if (matchOpt.isPresent() && matchOpt.get().data().status() == SpotStatus.RESERVED) {
            TreeMatchResult match = matchOpt.get();
            Spot spot = lot.getSpots().stream()
                    .filter(s -> s.getCode().equals(match.data().spotCode()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Vaga reservada nao localizada: " + match.data().spotCode()));

            spot.occupy(match.data().plate(), match.data().color(), match.data().driver());
            spotRepository.save(spot);

            reservationRepository.findFirstByLotIdAndSpotCodeAndStatusIn(
                    lot.getId(), spot.getCode(), List.of(ReservationStatus.RESERVED)
            ).ifPresent(res -> {
                res.markActiveOnGateEntry();
                reservationRepository.save(res);
            });

            treeIndexService.rebuildLotIndex(lot);

            return new GateCameraEventResponse(
                    "ALPR_GATE_ENTRY_MATCHED",
                    match.matchType(),
                    detectedPlate,
                    detectedColor,
                    match.data().plate(),
                    spot.getCode(),
                    null,
                    "OPEN_BARRIER_IMMEDIATELY",
                    null,
                    String.format("ENTRADA LIBERADA [%s]: Placa %s (%s) ocupou a vaga reservada %s.",
                            match.matchType(), detectedPlate, detectedColor, spot.getCode())
            );
        }

        // Entrada avulsa (sem reserva previa no App): aloca primeira vaga livre
        Spot freeSpot = lot.getSpots().stream()
                .filter(s -> s.getStatus() == SpotStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Estacionamento lotado!"));

        String driver = (driverName != null && !driverName.isBlank()) ? driverName.trim() : "Motorista Avulso";
        freeSpot.occupy(detectedPlate, detectedColor, driver);
        spotRepository.save(freeSpot);

        treeIndexService.rebuildLotIndex(lot);

        return new GateCameraEventResponse(
                "ALPR_GATE_ENTRY_DIRECT",
                "DIRECT_SPOT_ALLOCATION",
                detectedPlate,
                detectedColor,
                detectedPlate,
                freeSpot.getCode(),
                null,
                "OPEN_BARRIER_AVULSO",
                null,
                String.format("ENTRADA DIRETA: Placa %s (%s) alocada na vaga %s e indexada na Arvore.",
                        detectedPlate, detectedColor, freeSpot.getCode())
        );
    }

    private GateCameraEventResponse handleExit(
            ParkingLot lot,
            String detectedPlate,
            Optional<TreeMatchResult> matchOpt) {

        TreeMatchResult match = matchOpt.orElseThrow(() -> new IllegalArgumentException(
                "Veiculo nao encontrado na Arvore de Busca deste patio! Verifique a placa ou simule a entrada primeiro."));

        Spot spot = lot.getSpots().stream()
                .filter(s -> s.getCode().equals(match.data().spotCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Vaga nao encontrada: " + match.data().spotCode()));

        BillingSplitService.SplitBreakdown split = billingSplitService.calculateCheckoutSplit(lot.getOwnerHourlyRate());
        billingSplitService.recordCheckoutTransaction(
                lot.getId(),
                spot.getCode(),
                match.data().plate(),
                lot.getOwnerHourlyRate()
        );

        spot.release();
        spotRepository.save(spot);

        reservationRepository.findFirstByLotIdAndSpotCodeAndStatusIn(
                lot.getId(), spot.getCode(), List.of(ReservationStatus.RESERVED, ReservationStatus.ACTIVE)
        ).ifPresent(res -> {
            res.markCompletedOnCheckout();
            reservationRepository.save(res);
        });

        treeIndexService.rebuildLotIndex(lot);

        return new GateCameraEventResponse(
                "ALPR_GATE_EXIT_CHECKOUT",
                match.matchType(),
                null,
                null,
                detectedPlate,
                null,
                spot.getCode(),
                "OPEN_EXIT_BARRIER_PAID",
                new BillingSplitPayload(
                        split.chargedFromCustomerBRL(),
                        split.transferredToParkingOwnerBRL(),
                        split.transferredToPlatformBRL()
                ),
                String.format("SAIDA & SPLIT: Placa %s liberou a vaga %s. Cobrado R$ %.2f (R$ %.2f Dono + R$ 1,00 Plataforma).",
                        detectedPlate,
                        spot.getCode(),
                        split.chargedFromCustomerBRL(),
                        split.transferredToParkingOwnerBRL())
        );
    }
}
