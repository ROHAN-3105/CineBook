package com.booking_service.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.booking_service.entity.Booking;
import com.booking_service.exception.ShowtimeNotFoundException;
import com.booking_service.repository.BookingRepository;

@Service
public class BookingService {
	private static final Logger log = LoggerFactory.getLogger(BookingService.class);
	
	@Autowired
	private BookingRepository bookingRepo;
	
	@Autowired
	private ShowtimeClient showtimeClient;
	
	public Booking bookTickets(Long userId, Long showtimeId, Integer seats) {
		if (showtimeId == null || seats == null || seats <= 0)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A showtime and positive seat count are required");
		if (!showtimeClient.doesShowtimeExist(showtimeId)) {
			throw new ShowtimeNotFoundException("Invalid showtimeId: Showtime does not exist");
		}
		
		var reservation=showtimeClient.reserve(new ShowtimeClient.ReservationRequest(showtimeId,seats));
		showtimeClient.confirm(reservation.reservationId());
		
		Booking booking = new Booking();
		booking.setUserId(userId);
		booking.setShowtimeId(showtimeId);
		booking.setSeatsBooked(seats);
		
		try {
			return bookingRepo.save(booking);
		} catch (RuntimeException ex) {
			log.error("Reservation {} was confirmed but booking persistence failed; reconcile showtime {} quantity {}",
					reservation.reservationId(), showtimeId, seats, ex);
			throw ex;
		}
	}
	
	public List<Booking> getBookingsByUserId(Long userId) {
        return bookingRepo.findByUserId(userId);
    }
	
	public List<Booking> getBookingByShowtimeId(Long showtimeId){
		return bookingRepo.findByShowtimeId(showtimeId);
	}
}
