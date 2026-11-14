# Eureka Server

The discovery registry for the CineBook services. Spring Boot 3.4.0 / Spring Cloud Netflix Eureka Server, Java 17. Fixed port **8762**; no MySQL or Redis. It does not register itself (`registerWithEureka=false`, `fetchRegistry=false`). Dashboard: `http://localhost:8762/`. Start this first from the project root with `.\service.ps1 -Service eureka-server`, or from this directory with `.\mvnw.cmd spring-boot:run`. The root script loads the ignored local configuration. Run tests with `.\mvnw.cmd clean test`.
