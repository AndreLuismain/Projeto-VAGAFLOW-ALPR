package com.vagaflow.alpr.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade que representa um estacionamento ou comercio parceiro B2B.
 */
@Entity
@Table(name = "parking_lots")
public class ParkingLot {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "distance_label", nullable = false, length = 60)
    private String distance;

    @Column(name = "owner_hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal ownerHourlyRate;

    @OneToMany(mappedBy = "parkingLot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("code ASC")
    private List<Spot> spots = new ArrayList<>();

    protected ParkingLot() {
    }

    public ParkingLot(String id, String name, String distance, BigDecimal ownerHourlyRate) {
        this.id = id;
        this.name = name;
        this.distance = distance;
        this.ownerHourlyRate = ownerHourlyRate;
    }

    public void addSpot(Spot spot) {
        spot.setParkingLot(this);
        this.spots.add(spot);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDistance() {
        return distance;
    }

    public BigDecimal getOwnerHourlyRate() {
        return ownerHourlyRate;
    }

    public List<Spot> getSpots() {
        return spots;
    }
}
