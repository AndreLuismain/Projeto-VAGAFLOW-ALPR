package com.vagaflow.alpr.service;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import com.vagaflow.alpr.domain.entity.Spot;
import com.vagaflow.alpr.domain.enums.SpotStatus;
import com.vagaflow.alpr.dto.CreateParkingLotRequest;
import com.vagaflow.alpr.dto.ParkingLotDto;
import com.vagaflow.alpr.dto.SpotDto;
import com.vagaflow.alpr.engine.ParkingTreeIndexService;
import com.vagaflow.alpr.repository.ParkingLotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servico de gerenciamento de patios de estacionamento (B2B) e vagas no radar (B2C).
 */
@Service
public class ParkingLotService {

    private final ParkingLotRepository parkingLotRepository;
    private final BillingSplitService billingSplitService;
    private final ParkingTreeIndexService treeIndexService;

    public ParkingLotService(
            ParkingLotRepository parkingLotRepository,
            BillingSplitService billingSplitService,
            ParkingTreeIndexService treeIndexService) {
        this.parkingLotRepository = parkingLotRepository;
        this.billingSplitService = billingSplitService;
        this.treeIndexService = treeIndexService;
    }

    @Transactional(readOnly = true)
    public List<ParkingLotDto> listAllParkingLots() {
        return parkingLotRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingLot getLotOrThrow(String lotId) {
        return parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("Estacionamento nao encontrado: " + lotId));
    }

    @Transactional
    public ParkingLotDto createParkingLot(CreateParkingLotRequest request) {
        long nextNumber = parkingLotRepository.count() + 1;
        String newLotId = String.format("LOT-%02d", nextNumber);
        String distance = (request.distance() != null && !request.distance().isBlank())
                ? request.distance().trim()
                : "300m";

        ParkingLot lot = new ParkingLot(
                newLotId,
                request.name().trim(),
                distance,
                request.ownerHourlyRate()
        );

        for (int i = 1; i <= request.spotsCount(); i++) {
            String spotCode = String.format("V-%02d", i);
            lot.addSpot(new Spot(spotCode, SpotStatus.AVAILABLE, null, null, null));
        }

        ParkingLot saved = parkingLotRepository.save(lot);
        treeIndexService.rebuildLotIndex(saved);
        return toDto(saved);
    }

    public ParkingLotDto toDto(ParkingLot lot) {
        BigDecimal totalHourly = billingSplitService.calculateTotalHourlyPrice(lot.getOwnerHourlyRate());
        BigDecimal noShowPenalty = billingSplitService.calculateNoShowSplit(lot.getOwnerHourlyRate()).chargedFromCustomerBRL();
        List<SpotDto> spots = lot.getSpots().stream()
                .map(s -> new SpotDto(s.getCode(), s.getStatus(), s.getPlate(), s.getColor(), s.getDriver()))
                .toList();

        return new ParkingLotDto(
                lot.getId(),
                lot.getName(),
                lot.getDistance(),
                lot.getOwnerHourlyRate(),
                totalHourly,
                noShowPenalty,
                spots
        );
    }
}
