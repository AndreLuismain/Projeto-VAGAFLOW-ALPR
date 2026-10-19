package com.vagaflow.alpr.service;

import com.vagaflow.alpr.domain.entity.FinancialTransaction;
import com.vagaflow.alpr.dto.FinancialMetricsDto;
import com.vagaflow.alpr.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servico responsavel pelas regras matematicas de precificacao B2B2C:
 * - Taxa Fixa da Plataforma (PLATFORM_FEE = R$ 1,00 por operacao liquidada)
 * - Calculo de Preco Hora Total (ownerHourlyRate + R$ 1,00)
 * - Calculo de Multa de No-Show de 15 minutos (50% do valor minimo de 1h, preservando R$ 1,00 da plataforma)
 */
@Service
public class BillingSplitService {

    public static final BigDecimal PLATFORM_FIXED_FEE_BRL = new BigDecimal("1.00");
    public static final BigDecimal TWO = new BigDecimal("2.00");
    public static final String MANDATORY_15MIN_WARNING =
            "Se em 15minutos nao estiver na vaga é cobrado metade do valor minimo!";

    private final FinancialTransactionRepository transactionRepository;

    public BillingSplitService(FinancialTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public record SplitBreakdown(
            BigDecimal chargedFromCustomerBRL,
            BigDecimal transferredToParkingOwnerBRL,
            BigDecimal transferredToPlatformBRL
    ) {
    }

    /**
     * Soma o valor base do dono do estacionamento com a taxa fixa de R$ 1,00 da plataforma.
     */
    public BigDecimal calculateTotalHourlyPrice(BigDecimal ownerHourlyRate) {
        return ownerHourlyRate.add(PLATFORM_FIXED_FEE_BRL).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o split de uma estadia normal de 1 hora (Checkout na cancela EXIT).
     */
    public SplitBreakdown calculateCheckoutSplit(BigDecimal ownerHourlyRate) {
        BigDecimal ownerClean = ownerHourlyRate.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCharged = calculateTotalHourlyPrice(ownerClean);
        return new SplitBreakdown(totalCharged, ownerClean, PLATFORM_FIXED_FEE_BRL);
    }

    /**
     * Calcula o split da multa de No-Show (50% do valor minimo de 1h apos 15 minutos):
     * - chargedFromCustomerBRL = totalHourlyRate / 2.0
     * - transferredToPlatformBRL = 1.00
     * - transferredToParkingOwnerBRL = chargedFromCustomerBRL - 1.00
     */
    public SplitBreakdown calculateNoShowSplit(BigDecimal ownerHourlyRate) {
        BigDecimal totalHourly = calculateTotalHourlyPrice(ownerHourlyRate);
        BigDecimal penaltyTotal = totalHourly.divide(TWO, 2, RoundingMode.HALF_UP);
        BigDecimal ownerShare = penaltyTotal.subtract(PLATFORM_FIXED_FEE_BRL).setScale(2, RoundingMode.HALF_UP);
        if (ownerShare.compareTo(BigDecimal.ZERO) < 0) {
            ownerShare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new SplitBreakdown(penaltyTotal, ownerShare, PLATFORM_FIXED_FEE_BRL);
    }

    @Transactional
    public FinancialTransaction recordCheckoutTransaction(String lotId, String spotCode, String plate, BigDecimal ownerHourlyRate) {
        SplitBreakdown split = calculateCheckoutSplit(ownerHourlyRate);
        FinancialTransaction tx = new FinancialTransaction(
                "ALPR_GATE_EXIT_CHECKOUT",
                lotId,
                spotCode,
                plate,
                split.chargedFromCustomerBRL(),
                split.transferredToParkingOwnerBRL(),
                split.transferredToPlatformBRL()
        );
        return transactionRepository.save(tx);
    }

    @Transactional
    public FinancialTransaction recordNoShowTransaction(String lotId, String spotCode, String plate, BigDecimal ownerHourlyRate) {
        SplitBreakdown split = calculateNoShowSplit(ownerHourlyRate);
        FinancialTransaction tx = new FinancialTransaction(
                "RESERVATION_EXPIRED_NO_SHOW_15MIN",
                lotId,
                spotCode,
                plate,
                split.chargedFromCustomerBRL(),
                split.transferredToParkingOwnerBRL(),
                split.transferredToPlatformBRL()
        );
        return transactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public FinancialMetricsDto getFinancialMetrics() {
        List<FinancialTransaction> all = transactionRepository.findAll();
        BigDecimal platformTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal partnerTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (FinancialTransaction tx : all) {
            platformTotal = platformTotal.add(tx.getTransferredToPlatformBrl());
            partnerTotal = partnerTotal.add(tx.getTransferredToOwnerBrl());
        }
        return new FinancialMetricsDto(platformTotal, partnerTotal, all.size());
    }
}
