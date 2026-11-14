package com.showtime_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.showtime_service.dtos.ReservationRequest;
import com.showtime_service.dtos.ReservationResponse;
import com.showtime_service.repository.ShowtimeRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class SeatReservationService {
    private static final String CREATE = """
        local expired=redis.call('ZRANGEBYSCORE',KEYS[1],'-inf',ARGV[1])
        for _,id in ipairs(expired) do
          local key='cinebook:reservation:'..id
          if redis.call('HGET',key,'status')=='ACTIVE' then redis.call('HSET',key,'status','EXPIRED') end
        end
        redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',ARGV[1])
        local held=0
        for _,id in ipairs(redis.call('ZRANGE',KEYS[1],0,-1)) do
          held=held+tonumber(redis.call('HGET','cinebook:reservation:'..id,'quantity') or '0')
        end
        if held+tonumber(ARGV[3])>tonumber(ARGV[2]) then return 0 end
        redis.call('HSET',KEYS[2],'showtimeId',ARGV[4],'quantity',ARGV[3],'owner',ARGV[5],'status','ACTIVE','expiresAt',ARGV[6])
        redis.call('EXPIRE',KEYS[2],86400)
        redis.call('ZADD',KEYS[1],ARGV[6],ARGV[7])
        return 1
        """;
    private static final String CLAIM = """
        local status=redis.call('HGET',KEYS[2],'status')
        if not status then return -1 end
        if redis.call('HGET',KEYS[2],'owner')~=ARGV[1] then return -2 end
        if status~='ACTIVE' then return -3 end
        if tonumber(redis.call('HGET',KEYS[2],'expiresAt'))<=tonumber(ARGV[2]) then
          redis.call('HSET',KEYS[2],'status','EXPIRED');redis.call('ZREM',KEYS[1],ARGV[3]);return -4
        end
        redis.call('HSET',KEYS[2],'status','CONFIRMING')
        redis.call('ZADD',KEYS[1],ARGV[4],ARGV[3])
        return 1
        """;
    private static final String FINISH = """
        if redis.call('HGET',KEYS[2],'status')~='CONFIRMING' then return 0 end
        redis.call('HSET',KEYS[2],'status',ARGV[1])
        if ARGV[1]=='CONFIRMED' then redis.call('ZREM',KEYS[1],ARGV[2])
        else redis.call('ZADD',KEYS[1],ARGV[3],ARGV[2]) end
        return 1
        """;
    private static final String CANCEL = """
        local status=redis.call('HGET',KEYS[2],'status')
        if not status then return -1 end
        if redis.call('HGET',KEYS[2],'owner')~=ARGV[1] then return -2 end
        if status~='ACTIVE' then return -3 end
        redis.call('HSET',KEYS[2],'status','CANCELLED')
        redis.call('ZREM',KEYS[1],ARGV[2])
        return 1
        """;
    private final StringRedisTemplate redis;
    private final ShowtimeRepository showtimes;
    private final ShowtimeService showtimeService;
    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${reservation.duration-seconds:300}") private long durationSeconds;
    public SeatReservationService(StringRedisTemplate redis, ShowtimeRepository showtimes, ShowtimeService showtimeService) {
        this.redis=redis;this.showtimes=showtimes;this.showtimeService=showtimeService;
    }
    private String owner(String token){
        try { return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))).build().parseClaimsJws(token.replaceFirst("^Bearer ","")).getBody().getSubject(); }
        catch(Exception e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid token");}
    }
    private String listKey(long showtimeId){return "cinebook:holds:"+showtimeId;}
    private String key(String id){return "cinebook:reservation:"+id;}
    private long execute(String script,List<String> keys,String...args){
        Long result=redis.execute(new DefaultRedisScript<>(script,Long.class),keys,(Object[])args);
        if(result==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Redis did not respond");
        return result;
    }
    public ReservationResponse reserve(ReservationRequest request,String token){
        if(request==null||request.showtimeId()==null||request.quantity()==null||request.quantity()<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A showtime and positive quantity are required");
        var show=showtimes.findById(request.showtimeId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Showtime not found"));
        String id=UUID.randomUUID().toString(),user=owner(token);
        long now=Instant.now().toEpochMilli(),expires=now+Duration.ofSeconds(durationSeconds).toMillis();
        long ok=execute(CREATE,List.of(listKey(show.getId()),key(id)),Long.toString(now),Integer.toString(show.getAvailableSeats()),Integer.toString(request.quantity()),show.getId().toString(),user,Long.toString(expires),id);
        if(ok!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Not enough tickets available");
        return new ReservationResponse(id,show.getId(),request.quantity(),"ACTIVE",Instant.ofEpochMilli(expires));
    }
    public ReservationResponse details(String id,String token){
        Map<Object,Object> values=redis.opsForHash().entries(key(id));
        if(values.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reservation not found");
        if(!owner(token).equals(values.get("owner")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your reservation");
        String status=(String)values.get("status");long expires=Long.parseLong((String)values.get("expiresAt"));
        if("ACTIVE".equals(status)&&Instant.now().toEpochMilli()>=expires)status="EXPIRED";
        return new ReservationResponse(id,Long.parseLong((String)values.get("showtimeId")),Integer.parseInt((String)values.get("quantity")),status,Instant.ofEpochMilli(expires));
    }
    public ReservationResponse confirm(String id,String token){
        ReservationResponse reservation=details(id,token);
        long claimed=execute(CLAIM,List.of(listKey(reservation.showtimeId()),key(id)),owner(token),Long.toString(Instant.now().toEpochMilli()),id,Long.toString(Instant.now().plus(Duration.ofHours(24)).toEpochMilli()));
        if(claimed!=1)throw new ResponseStatusException(claimed==-4?HttpStatus.GONE:HttpStatus.CONFLICT,"Reservation is unavailable for confirmation");
        try {
            showtimeService.bookSeats(reservation.showtimeId(),reservation.quantity());
        }catch(RuntimeException ex){
            execute(FINISH,List.of(listKey(reservation.showtimeId()),key(id)),"ACTIVE",id,Long.toString(reservation.expiresAt().toEpochMilli()));
            throw ex;
        }
        execute(FINISH,List.of(listKey(reservation.showtimeId()),key(id)),"CONFIRMED",id,Long.toString(reservation.expiresAt().toEpochMilli()));
        return details(id,token);
    }
    public ReservationResponse cancel(String id,String token){
        ReservationResponse reservation=details(id,token);
        long ok=execute(CANCEL,List.of(listKey(reservation.showtimeId()),key(id)),owner(token),id);
        if(ok!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation cannot be cancelled");
        return details(id,token);
    }
}
