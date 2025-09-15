package com.example.cloudfour.paymentservice.domain.payment.apiclient;

import com.example.cloudfour.paymentservice.commondto.UserResponseDTO;
import com.example.cloudfour.paymentservice.config.FeignConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name="user-service", url="http://user-service.app.svc.cluster.local:80/internal/users", configuration = FeignConfig.class)
@CircuitBreaker(name="user-circuit")
public interface UserFeginClient {
    @GetMapping("/{userId}/exists")
    Boolean existsUser(@PathVariable("userId") UUID userId);

    @GetMapping("/{userId}")
    UserResponseDTO getUserById(@PathVariable("userId") UUID userId);
}
