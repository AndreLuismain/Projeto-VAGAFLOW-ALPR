package com.vagaflow.alpr.domain.entity;

import com.vagaflow.alpr.domain.enums.PlateAngle;
import jakarta.persistence.*;

/**
 * Entidade que representa os dados cadastrais e opticos do veiculo do motorista.
 */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @Column(name = "license_plate", length = 10, nullable = false)
    private String licensePlate;

    @Column(name = "driver_name", nullable = false, length = 80)
    private String driverName;

    @Column(name = "vehicle_color", nullable = false, length = 24)
    private String vehicleColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "plate_angle", nullable = false, length = 16)
    private PlateAngle plateAngle;

    @Column(name = "photo_url", nullable = false, length = 255)
    private String photoUrl;

    protected Vehicle() {
    }

    public Vehicle(String licensePlate, String driverName, String vehicleColor, PlateAngle plateAngle) {
        this.licensePlate = licensePlate.toUpperCase().replaceAll("[^A-Z0-9]", "");
        this.driverName = driverName;
        this.vehicleColor = vehicleColor.toUpperCase();
        this.plateAngle = plateAngle != null ? plateAngle : PlateAngle.TRASEIRA;
        this.photoUrl = String.format("https://cdn.vagaflow.app/plates/%s_%s.jpg",
                this.licensePlate, this.plateAngle.name().toLowerCase());
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public PlateAngle getPlateAngle() {
        return plateAngle;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
