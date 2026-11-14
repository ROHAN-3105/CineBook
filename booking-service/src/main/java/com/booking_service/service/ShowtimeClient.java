package com.booking_service.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.Instant;

import com.booking_service.config.FeignClientConfig;

@FeignClient(name = "showtime-service", path = "/api/showtimes", configuration = FeignClientConfig.class)
public interface ShowtimeClient {
	
	@GetMapping("/{id}/exists")
    Boolean doesShowtimeExist(@PathVariable("id") Long id);
	
	@PutMapping("/{showtimeId}/book-seats/{seats}")
	void bookSeats(@PathVariable("showtimeId") Long showtimeId, @PathVariable("seats") Integer seats);

	@PostMapping("/reservations")
	ReservationResponse reserve(@RequestBody ReservationRequest request);

	@PostMapping("/reservations/{id}/confirm")
	ReservationResponse confirm(@PathVariable("id") String id);

	@PostMapping("/reservations/{id}/cancel")
	ReservationResponse cancel(@PathVariable("id") String id);

	record ReservationRequest(Long showtimeId,Integer quantity) {}
	record ReservationResponse(String reservationId,Long showtimeId,int quantity,String status,Instant expiresAt) {}
}
