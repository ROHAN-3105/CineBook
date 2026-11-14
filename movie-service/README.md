# Movie Service

Spring Boot MVC/JPA movie catalog, registered with Eureka on a dynamic port. Requires MySQL `movie_ticketing`, `SPRING_DATASOURCE_PASSWORD`, the shared Base64 `JWT_SECRET`, and Eureka. Authenticated `GET /api/movies` and `GET /api/movies/{id}` return the catalog; `GET /api/movies/{id}/exists` supports Showtime's Feign validation. ADMIN may POST `/api/movies/admin`, PUT or DELETE `/api/movies/admin/{id}`. From the root run `.\service.ps1 -Service movie-service` (or `.\mvnw.cmd spring-boot:run` here with equivalent configuration). Test with `.\mvnw.cmd clean test`.
