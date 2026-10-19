package com.vagaflow.alpr.controller;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import com.vagaflow.alpr.dto.GateCameraEventRequest;
import com.vagaflow.alpr.dto.GateCameraEventResponse;
import com.vagaflow.alpr.engine.ParkingTreeIndexService;
import com.vagaflow.alpr.engine.ParkingTreeIndexService.TreeMatchResult;
import com.vagaflow.alpr.service.GateAccessService;
import com.vagaflow.alpr.service.ParkingLotService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints REST do Simulador de Camera ALPR (ENTRY, EXIT, Fuzzy OCR Hamming <= 1 e Visualizacao da Arvore).
 */
@RestController
@RequestMapping("/api/v1/gate")
public class GateCameraController {

    private final GateAccessService gateAccessService;
    private final ParkingLotService parkingLotService;
    private final ParkingTreeIndexService treeIndexService;

    public GateCameraController(
            GateAccessService gateAccessService,
            ParkingLotService parkingLotService,
            ParkingTreeIndexService treeIndexService) {
        this.gateAccessService = gateAccessService;
        this.parkingLotService = parkingLotService;
        this.treeIndexService = treeIndexService;
    }

    @PostMapping("/events")
    public ResponseEntity<GateCameraEventResponse> processGateEvent(
            @Valid @RequestBody GateCameraEventRequest request) {
        return ResponseEntity.ok(gateAccessService.processGateEvent(request));
    }

    @GetMapping("/tree/{lotId}")
    public ResponseEntity<Map<String, Object>> getTreeVisualization(@PathVariable String lotId) {
        ParkingLot lot = parkingLotService.getLotOrThrow(lotId);
        treeIndexService.rebuildLotIndex(lot);
        List<String> lines = treeIndexService.dumpTreeLines(lot.getId(), lot.getName());
        return ResponseEntity.ok(Map.of(
                "lotId", lot.getId(),
                "lotName", lot.getName(),
                "complexity", "O(L) | L = 7 chars",
                "treeLines", lines,
                "formattedTree", String.join("\n", lines)
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> testTreeSearch(
            @RequestParam String lotId,
            @RequestParam String color,
            @RequestParam String plate) {
        ParkingLot lot = parkingLotService.getLotOrThrow(lotId);
        treeIndexService.rebuildLotIndex(lot);
        Optional<TreeMatchResult> match = treeIndexService.search(lot.getId(), color, plate);
        if (match.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "found", true,
                    "matchType", match.get().matchType(),
                    "vehicleData", match.get().data()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "found", false,
                "detectedPlate", plate,
                "detectedColor", color
        ));
    }
}
