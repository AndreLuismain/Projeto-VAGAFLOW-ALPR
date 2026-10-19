package com.vagaflow.alpr.config;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import com.vagaflow.alpr.domain.entity.Spot;
import com.vagaflow.alpr.domain.enums.SpotStatus;
import com.vagaflow.alpr.engine.ParkingTreeIndexService;
import com.vagaflow.alpr.repository.ParkingLotRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Inicializa o banco de dados em memoria e a Arvore de Busca ALPR com os dois patios
 * padrao fieis ao prototipo funcional ("Estacionamento Centro Rapido" e "Farmacia & Comercio Central").
 */
@Configuration
public class DataInitializerConfig {

    @Bean
    public CommandLineRunner seedInitialParkingLots(
            ParkingLotRepository parkingLotRepository,
            ParkingTreeIndexService treeIndexService) {
        return args -> {
            if (parkingLotRepository.count() > 0) {
                return;
            }

            // LOT-01: Dono pede R$ 7,00/h -> App mostra R$ 8,00/h -> No-show cobra R$ 4,00 (R$ 3,00 dono + R$ 1,00 plataforma)
            ParkingLot lot1 = new ParkingLot(
                    "LOT-01",
                    "Estacionamento Centro Rápido",
                    "180m (2 quadras)",
                    new BigDecimal("7.00")
            );
            lot1.addSpot(new Spot("A-01", SpotStatus.OCCUPIED, "QWE9A88", "PRETO", "Avulso Local"));
            lot1.addSpot(new Spot("A-02", SpotStatus.OCCUPIED, "ERT4B11", "BRANCO", "Avulso Local"));
            lot1.addSpot(new Spot("A-03", SpotStatus.AVAILABLE, null, null, null));
            lot1.addSpot(new Spot("A-04", SpotStatus.AVAILABLE, null, null, null));
            lot1.addSpot(new Spot("A-05", SpotStatus.AVAILABLE, null, null, null));
            lot1.addSpot(new Spot("A-06", SpotStatus.AVAILABLE, null, null, null));
            lot1.addSpot(new Spot("A-07", SpotStatus.AVAILABLE, null, null, null));
            lot1.addSpot(new Spot("A-08", SpotStatus.AVAILABLE, null, null, null));

            // LOT-02: Dono pede R$ 5,00/h -> App mostra R$ 6,00/h -> No-show cobra R$ 3,00 (R$ 2,00 dono + R$ 1,00 plataforma)
            ParkingLot lot2 = new ParkingLot(
                    "LOT-02",
                    "Farmácia & Comércio Central",
                    "210m (2 quadras)",
                    new BigDecimal("5.00")
            );
            lot2.addSpot(new Spot("F-01", SpotStatus.OCCUPIED, "MNB7C22", "PRATA", "Cliente Farmácia"));
            lot2.addSpot(new Spot("F-02", SpotStatus.AVAILABLE, null, null, null));
            lot2.addSpot(new Spot("F-03", SpotStatus.AVAILABLE, null, null, null));
            lot2.addSpot(new Spot("F-04", SpotStatus.AVAILABLE, null, null, null));

            parkingLotRepository.save(lot1);
            parkingLotRepository.save(lot2);

            treeIndexService.rebuildLotIndex(lot1);
            treeIndexService.rebuildLotIndex(lot2);
        };
    }
}
