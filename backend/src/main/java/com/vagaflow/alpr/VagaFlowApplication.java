package com.vagaflow.alpr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da API VagaFlow ALPR.
 * Habilita agendamento (@EnableScheduling) para verificacao automatica
 * de reservas expiradas apos 15 minutos (No-Show) e split financeiro.
 */
@SpringBootApplication
@EnableScheduling
public class VagaFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(VagaFlowApplication.class, args);
    }
}
