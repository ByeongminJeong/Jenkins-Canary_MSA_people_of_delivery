package com.example.cloudfour.apigateway.client;

import com.example.cloudfour.apigateway.config.FeignConfig;
import com.example.cloudfour.apigateway.dto.PassportRequestDTO;
import com.example.cloudfour.apigateway.dto.PassportResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Lazy
@FeignClient(
    name = "auth-service",
    url = "http://auth-service.app.svc.cluster.local:80/internal/auth",
    configuration = FeignConfig.class
)
public interface PassportClient {
    
    @PostMapping("/api/passports")
    PassportResponseDTO createPassport(@RequestBody PassportRequestDTO request);
    
    @GetMapping("/api/passports/{passportId}/validate")
    Boolean validatePassport(@PathVariable("passportId") String passportId);
}
