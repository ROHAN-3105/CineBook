package com.booking_service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.booking_service.entity.Booking;
import com.booking_service.service.BookingService;
import com.booking_service.service.CurrentUserClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
	
	@Autowired
	private BookingService bookingService;
	@Autowired private CurrentUserClient currentUserClient;
	
	@PostMapping
	public Booking bookTickets(@RequestBody Booking booking) {
		if (booking.getUserId() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Do not supply userId");
		Long userId = currentUserClient.currentUser().id();
		Long showtimeId = booking.getShowtimeId();
		Integer numberOfSeats = booking.getSeatsBooked();
		
		return bookingService.bookTickets(userId, showtimeId, numberOfSeats);
	}

	@GetMapping("/mine")
	public List<Booking> mine() { return bookingService.getBookingsByUserId(currentUserClient.currentUser().id()); }
	
	@GetMapping("/{userId}")
	public List<Booking> getBookingsByUserId(@PathVariable Long userId){
		if (!userId.equals(currentUserClient.currentUser().id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another user's bookings");
		return bookingService.getBookingsByUserId(userId);
	}
	
	@GetMapping
	public List<Booking> getBookingByShowtimeId(@RequestParam("showtimeId") Long showtimeId){
		Long currentUserId = currentUserClient.currentUser().id();
		return bookingService.getBookingByShowtimeId(showtimeId).stream()
				.filter(b -> b.getUserId().equals(currentUserId)).toList();
	}
}
