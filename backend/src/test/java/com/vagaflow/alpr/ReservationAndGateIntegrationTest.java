package com.vagaflow.alpr;

import com.vagaflow.alpr.domain.enums.GateDirection;
import com.vagaflow.alpr.domain.enums.PlateAngle;
import com.vagaflow.alpr.dto.CreateReservationRequest;
import com.vagaflow.alpr.dto.GateCameraEventRequest;
import com.vagaflow.alpr.dto.GateCameraEventResponse;
import com.vagaflow.alpr.dto.NoShowExpiredResponse;
import com.vagaflow.alpr.dto.ReservationSyncGateResponse;
import com.vagaflow.alpr.service.BillingSplitService;
import com.vagaflow.alpr.service.GateAccessService;
import com.vagaflow.alpr.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste integrado ponta a ponta validando Reserva de 15 minutos, Sync-Gate com a Arvore ALPR,
 * Entrada/Saida pela Cancela e Expiracao de No-Show com Split Financeiro.
 */
@SpringBootTest
class ReservationAndGateIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private GateAccessService gateAccessService;

    @Autowired
    private BillingSplitService billingSplitService;

    @Test
    @DisplayName("Fluxo Completo 1: Reserva 15m -> Sync Gate -> Camera ENTRY (com Fuzzy OCR) -> Camera EXIT + Split")
    void shouldExecuteFullReservationEntryAndExitLifecycle() {
        // 1. Cria reserva para placa BRA2E19 (PRATA) no LOT-01 (valor dono R$ 7,00 -> total R$ 8,00)
        ReservationSyncGateResponse sync = reservationService.createAndSyncGate(
                new CreateReservationRequest("LOT-01", "André Luís", "BRA2E19", "PRATA", PlateAngle.TRASEIRA)
        );

        assertEquals("RESERVATION_CREATED_LOCK_SPOT", sync.event());
        assertEquals("A-03", sync.parkingLotSync().allocatedSpotCode());
        assertEquals("RESERVED", sync.parkingLotSync().temporaryLockStatus());
        assertTrue(sync.parkingLotSync().indexedInColorPrefixTree());
        assertEquals(new BigDecimal("8.00"), sync.parkingLotSync().pricingRule().minimumHourlyTotalBRL());
        assertEquals(new BigDecimal("4.00"), sync.parkingLotSync().pricingRule().noShow15MinPenaltyTotalBRL());

        // 2. Camera de entrada sofre erro de 1 caractere no OCR ('BRA2E18' em vez de 'BRA2E19'), mas recupera na Arvore
        GateCameraEventResponse entry = gateAccessService.processGateEvent(
                new GateCameraEventRequest("LOT-01", GateDirection.ENTRY, "BRA2E18", "PRATA", "André Luís")
        );

        assertEquals("ALPR_GATE_ENTRY_MATCHED", entry.event());
        assertTrue(entry.treeLookupStrategy().startsWith("FUZZY_COLOR_TREE_MATCH"));
        assertEquals("A-03", entry.spotActivated());
        assertEquals("OPEN_BARRIER_IMMEDIATELY", entry.gateCommand());

        // 3. Camera de saida realiza o checkout e liquida o split (R$ 7,00 dono + R$ 1,00 plataforma)
        GateCameraEventResponse exit = gateAccessService.processGateEvent(
                new GateCameraEventRequest("LOT-01", GateDirection.EXIT, "BRA2E19", "PRATA", null)
        );

        assertEquals("ALPR_GATE_EXIT_CHECKOUT", exit.event());
        assertEquals("A-03", exit.releasedSpot());
        assertEquals(new BigDecimal("8.00"), exit.billingSplit().totalChargedBRL());
        assertEquals(new BigDecimal("7.00"), exit.billingSplit().parkingOwnerShareBRL());
        assertEquals(new BigDecimal("1.00"), exit.billingSplit().platformFixedFeeBRL());
    }

    @Test
    @DisplayName("Fluxo Completo 2: Reserva 15m -> Expiracao No-Show sem aparecer na camera -> Multa 50% e Liberacao da Vaga")
    void shouldExecuteNoShowPenaltyWhen15MinutesExpire() {
        ReservationSyncGateResponse sync = reservationService.createAndSyncGate(
                new CreateReservationRequest("LOT-02", "Motorista Atrasado", "XYZ1A23", "AZUL", PlateAngle.FRONTAL)
        );

        NoShowExpiredResponse noShow = reservationService.triggerNoShowPenalty(sync.reservationId());

        assertEquals("RESERVATION_EXPIRED_NO_SHOW_15MIN", noShow.event());
        assertEquals(sync.parkingLotSync().allocatedSpotCode(), noShow.spotReleased());
        // LOT-02: Dono R$ 5,00 + R$ 1,00 App = R$ 6,00 -> Multa 50% = R$ 3,00 (R$ 2,00 Dono + R$ 1,00 App)
        assertEquals(new BigDecimal("3.00"), noShow.automaticSplitCharge().chargedFromCustomerBRL());
        assertEquals(new BigDecimal("2.00"), noShow.automaticSplitCharge().transferredToParkingOwnerBRL());
        assertEquals(new BigDecimal("1.00"), noShow.automaticSplitCharge().transferredToPlatformBRL());
    }
}
