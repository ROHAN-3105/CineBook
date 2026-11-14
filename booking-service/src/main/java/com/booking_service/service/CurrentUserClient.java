package com.booking_service.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import com.booking_service.config.FeignClientConfig;

@FeignClient(name = "user-service", path = "/api/user", configuration = FeignClientConfig.class)
public interface CurrentUserClient {
    @GetMapping("/me")
    CurrentUser currentUser();
    record CurrentUser(Long id, String username, String role) {}
}
