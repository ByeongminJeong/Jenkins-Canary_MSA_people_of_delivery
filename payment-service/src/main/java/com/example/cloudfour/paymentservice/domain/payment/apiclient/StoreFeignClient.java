package com.example.cloudfour.paymentservice.domain.payment.apiclient;

import com.example.cloudfour.paymentservice.commondto.StoreResponseDTO;
import com.example.cloudfour.paymentservice.config.FeignConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "store-service",
        url="http://store-service.app.svc.cluster.local:80/internal/stores",
        configuration = FeignConfig.class
)
@CircuitBreaker(name="store-circuit")
public interface StoreFeignClient {
    @GetMapping("/{storeId}/exists")
    Boolean existsStore(@PathVariable("storeId") UUID storeId);

    @GetMapping("/{storeId}")
    StoreResponseDTO getStoreById(@PathVariable("storeId") UUID storeId);
}
