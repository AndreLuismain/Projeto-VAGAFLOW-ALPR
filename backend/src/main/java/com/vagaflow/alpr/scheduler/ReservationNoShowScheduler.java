package com.vagaflow.alpr.scheduler;

import com.vagaflow.alpr.domain.entity.Reservation;
import com.vagaflow.alpr.domain.enums.ReservationStatus;
import com.vagaflow.alpr.repository.ReservationRepository;
import com.vagaflow.alpr.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Job agendado que verifica periodicamente reservas cujo TTL de 15 minutos expirou
 * sem leitura da placa na cancela de entrada (No-Show) e aplica a multa automatica de 50%.
 */
@Component
public class ReservationNoShowScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationNoShowScheduler.class);

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public ReservationNoShowScheduler(
            ReservationRepository reservationRepository,
            ReservationService reservationService) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelay = 10000)
    public void processExpiredReservations() {
        List<Reservation> expiredList = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.RESERVED, Instant.now());

        for (Reservation expired : expiredList) {
            try {
                var result = reservationService.triggerNoShowPenalty(expired.getReservationId());
                log.info("No-Show processado automaticamente para reserva {} (Placa {}, Vaga {}): R$ {}",
                        result.reservationId(),
                        result.licensePlate(),
                        result.spotReleased(),
                        result.automaticSplitCharge().chargedFromCustomerBRL());
            } catch (Exception e) {
                log.warn("Falha ao processar expiracao da reserva {}: {}", expired.getReservationId(), e.getMessage());
            }
        }
    }
}
