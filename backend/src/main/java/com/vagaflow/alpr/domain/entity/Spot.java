package com.vagaflow.alpr.domain.entity;

import com.vagaflow.alpr.domain.enums.SpotStatus;
import jakarta.persistence.*;

/**
 * Entidade que representa uma vaga fisica individual dentro de um patio de estacionamento.
 */
@Entity
@Table(name = "spots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lot_spot_code", columnNames = {"parking_lot_id", "code"})
})
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parking_lot_id", nullable = false)
    private ParkingLot parkingLot;

    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SpotStatus status;

    @Column(name = "plate", length = 10)
    private String plate;

    @Column(name = "color", length = 24)
    private String color;

    @Column(name = "driver", length = 80)
    private String driver;

    protected Spot() {
    }

    public Spot(String code, SpotStatus status, String plate, String color, String driver) {
        this.code = code;
        this.status = status;
        this.plate = plate != null ? plate.toUpperCase() : null;
        this.color = color != null ? color.toUpperCase() : null;
        this.driver = driver;
    }

    public void allocateReserved(String plate, String color, String driver) {
        this.status = SpotStatus.RESERVED;
        this.plate = plate.toUpperCase();
        this.color = color.toUpperCase();
        this.driver = driver;
    }

    public void occupy(String plate, String color, String driver) {
        this.status = SpotStatus.OCCUPIED;
        this.plate = plate.toUpperCase();
        this.color = color.toUpperCase();
        if (driver != null && !driver.isBlank()) {
            this.driver = driver;
        }
    }

    public void release() {
        this.status = SpotStatus.AVAILABLE;
        this.plate = null;
        this.color = null;
        this.driver = null;
    }

    public Long getId() {
        return id;
    }

    public ParkingLot getParkingLot() {
        return parkingLot;
    }

    public void setParkingLot(ParkingLot parkingLot) {
        this.parkingLot = parkingLot;
    }

    public String getCode() {
        return code;
    }

    public SpotStatus getStatus() {
        return status;
    }

    public String getPlate() {
        return plate;
    }

    public String getColor() {
        return color;
    }

    public String getDriver() {
        return driver;
    }
}
