package com.showtime_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.showtime_service.dtos.ReservationRequest;
import com.showtime_service.dtos.ReservationResponse;
import com.showtime_service.service.SeatReservationService;

@RestController
@RequestMapping("/api/showtimes/reservations")
public class SeatReservationController {
    private final SeatReservationService reservations;
    public SeatReservationController(SeatReservationService reservations){this.reservations=reservations;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@RequestBody ReservationRequest request,@RequestHeader("Authorization") String token){return reservations.reserve(request,token);}
    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable String id,@RequestHeader("Authorization") String token){return reservations.details(id,token);}
    @PostMapping("/{id}/confirm")
    public ReservationResponse confirm(@PathVariable String id,@RequestHeader("Authorization") String token){return reservations.confirm(id,token);}
    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable String id,@RequestHeader("Authorization") String token){return reservations.cancel(id,token);}
}
