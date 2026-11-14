package com.showtime_service.dtos;
import java.time.Instant;
public record ReservationResponse(String reservationId, Long showtimeId, int quantity, String status, Instant expiresAt) {}
