package com.vagaflow.alpr.service;

import com.vagaflow.alpr.repository.FinancialTransactionRepository;
import com.vagaflow.alpr.service.BillingSplitService.SplitBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Suite de testes unitarios para as regras financeiras de Markup Fixo (R$ 1,00)
 * e Split Automatico de No-Show (50% do valor minimo de 1h).
 */
@ExtendWith(MockitoExtension.class)
class BillingSplitServiceTest {

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @InjectMocks
    private BillingSplitService billingSplitService;

    @Test
    @DisplayName("Deve somar R$ 1,00 fixo da plataforma ao valor base hora do dono do estacionamento")
    void shouldAddOneRealPlatformFeeToOwnerHourlyRate() {
        BigDecimal totalLot1 = billingSplitService.calculateTotalHourlyPrice(new BigDecimal("7.00"));
        BigDecimal totalLot2 = billingSplitService.calculateTotalHourlyPrice(new BigDecimal("5.00"));

        assertEquals(new BigDecimal("8.00"), totalLot1);
        assertEquals(new BigDecimal("6.00"), totalLot2);
    }

    @Test
    @DisplayName("Deve calcular o split de Checkout normal (R$ 7,00 dono + R$ 1,00 plataforma = R$ 8,00)")
    void shouldCalculateNormalCheckoutSplitCorrectly() {
        SplitBreakdown split = billingSplitService.calculateCheckoutSplit(new BigDecimal("7.00"));

        assertEquals(new BigDecimal("8.00"), split.chargedFromCustomerBRL());
        assertEquals(new BigDecimal("7.00"), split.transferredToParkingOwnerBRL());
        assertEquals(new BigDecimal("1.00"), split.transferredToPlatformBRL());
    }

    @Test
    @DisplayName("Deve calcular a multa de No-Show de 15 minutos (50% de R$ 8,00 = R$ 4,00 -> R$ 3,00 dono + R$ 1,00 plataforma)")
    void shouldCalculateNoShow50PercentSplitPreservingOneRealPlatformFee() {
        // Caso 1: Dono pede R$ 7,00/h -> Total App = R$ 8,00/h -> Multa 50% = R$ 4,00 (R$ 3,00 dono + R$ 1,00 plataforma)
        SplitBreakdown split7 = billingSplitService.calculateNoShowSplit(new BigDecimal("7.00"));
        assertEquals(new BigDecimal("4.00"), split7.chargedFromCustomerBRL());
        assertEquals(new BigDecimal("3.00"), split7.transferredToParkingOwnerBRL());
        assertEquals(new BigDecimal("1.00"), split7.transferredToPlatformBRL());

        // Caso 2: Dono pede R$ 5,00/h -> Total App = R$ 6,00/h -> Multa 50% = R$ 3,00 (R$ 2,00 dono + R$ 1,00 plataforma)
        SplitBreakdown split5 = billingSplitService.calculateNoShowSplit(new BigDecimal("5.00"));
        assertEquals(new BigDecimal("3.00"), split5.chargedFromCustomerBRL());
        assertEquals(new BigDecimal("2.00"), split5.transferredToParkingOwnerBRL());
        assertEquals(new BigDecimal("1.00"), split5.transferredToPlatformBRL());
    }
}
