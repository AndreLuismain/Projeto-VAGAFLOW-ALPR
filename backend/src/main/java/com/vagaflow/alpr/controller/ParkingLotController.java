package com.vagaflow.alpr.controller;

import com.vagaflow.alpr.dto.CreateParkingLotRequest;
import com.vagaflow.alpr.dto.FinancialMetricsDto;
import com.vagaflow.alpr.dto.ParkingLotDto;
import com.vagaflow.alpr.service.BillingSplitService;
import com.vagaflow.alpr.service.ParkingLotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints REST para listagem de estacionamentos no radar B2C,
 * cadastro de novos patios no portal B2B e consulta dos contadores financeiros.
 */
@RestController
@RequestMapping("/api/v1/parking-lots")
public class ParkingLotController {

    private final ParkingLotService parkingLotService;
    private final BillingSplitService billingSplitService;

    public ParkingLotController(
            ParkingLotService parkingLotService,
            BillingSplitService billingSplitService) {
        this.parkingLotService = parkingLotService;
        this.billingSplitService = billingSplitService;
    }

    @GetMapping
    public ResponseEntity<List<ParkingLotDto>> listParkingLots() {
        return ResponseEntity.ok(parkingLotService.listAllParkingLots());
    }

    @PostMapping
    public ResponseEntity<ParkingLotDto> createParkingLot(@Valid @RequestBody CreateParkingLotRequest request) {
        ParkingLotDto created = parkingLotService.createParkingLot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/metrics")
    public ResponseEntity<FinancialMetricsDto> getMetrics() {
        return ResponseEntity.ok(billingSplitService.getFinancialMetrics());
    }
}
