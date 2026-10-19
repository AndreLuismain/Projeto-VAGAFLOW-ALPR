package com.vagaflow.alpr.domain.entity;

import com.vagaflow.alpr.domain.enums.PlateAngle;
import com.vagaflow.alpr.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidade que representa uma reserva temporaria de vaga (TTL de 15 minutos)
 * realizada pelo motorista no App B2C.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "reservation_id", length = 32, nullable = false)
    private String reservationId;

    @Column(name = "lot_id", nullable = false, length = 32)
    private String lotId;

    @Column(name = "lot_name", nullable = false, length = 120)
    private String lotName;

    @Column(name = "spot_code", nullable = false, length = 16)
    private String spotCode;

    @Column(name = "driver_name", nullable = false, length = 80)
    private String driverName;

    @Column(name = "license_plate", nullable = false, length = 10)
    private String licensePlate;

    @Column(name = "vehicle_color", nullable = false, length = 24)
    private String vehicleColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_angle", nullable = false, length = 16)
    private PlateAngle photoAngle;

    @Column(name = "owner_hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal ownerHourlyRate;

    @Column(name = "total_hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHourlyRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ReservationStatus status;

    protected Reservation() {
    }

    public Reservation(
            String reservationId,
            String lotId,
            String lotName,
            String spotCode,
            String driverName,
            String licensePlate,
            String vehicleColor,
            PlateAngle photoAngle,
            BigDecimal ownerHourlyRate,
            BigDecimal totalHourlyRate,
            Instant createdAt,
            Instant expiresAt) {
        this.reservationId = reservationId;
        this.lotId = lotId;
        this.lotName = lotName;
        this.spotCode = spotCode;
        this.driverName = driverName;
        this.licensePlate = licensePlate.toUpperCase();
        this.vehicleColor = vehicleColor.toUpperCase();
        this.photoAngle = photoAngle;
        this.ownerHourlyRate = ownerHourlyRate;
        this.totalHourlyRate = totalHourlyRate;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.RESERVED;
    }

    public void markActiveOnGateEntry() {
        this.status = ReservationStatus.ACTIVE;
    }

    public void markCompletedOnCheckout() {
        this.status = ReservationStatus.COMPLETED;
    }

    public void markExpiredNoShow() {
        this.status = ReservationStatus.EXPIRED_NO_SHOW;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getLotId() {
        return lotId;
    }

    public String getLotName() {
        return lotName;
    }

    public String getSpotCode() {
        return spotCode;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public PlateAngle getPhotoAngle() {
        return photoAngle;
    }

    public BigDecimal getOwnerHourlyRate() {
        return ownerHourlyRate;
    }

    public BigDecimal getTotalHourlyRate() {
        return totalHourlyRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
