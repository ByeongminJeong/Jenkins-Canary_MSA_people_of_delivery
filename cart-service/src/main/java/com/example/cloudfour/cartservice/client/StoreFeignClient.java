package com.example.cloudfour.cartservice.client;

import com.example.cloudfour.cartservice.commondto.MenuOptionResponseDTO;
import com.example.cloudfour.cartservice.commondto.MenuQuantityResponseDTO;
import com.example.cloudfour.cartservice.commondto.MenuResponseDTO;
import com.example.cloudfour.cartservice.commondto.StoreResponseDTO;
import com.example.cloudfour.cartservice.config.FeignConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "store-service",
        url="http://store-service.app.svc.cluster.local:80/internal",
        configuration = FeignConfig.class
)
@CircuitBreaker(name = "store-circuit")
public interface StoreFeignClient {
    @RequestMapping(method = RequestMethod.HEAD, value = "/stores/exists")
    void checkStoreExists(@RequestParam("storeId") UUID storeId);

    @RequestMapping(method = RequestMethod.HEAD, value = "/menus/exists")
    void checkMenuExists(@RequestParam("menuId") UUID menuId);

    @GetMapping("/stores/{storeId}")
    StoreResponseDTO storeById(@PathVariable("storeId") UUID storeId);

    @GetMapping("/menus/{menuId}")
    MenuResponseDTO menuById(@PathVariable("menuId") UUID menuId);

    @GetMapping("/menus/options/{optionId}/detail")
    MenuOptionResponseDTO menuOptionById(@PathVariable("optionId") UUID menuOptionId);

    @GetMapping("/menus/{menuId}/stock")
    MenuQuantityResponseDTO getMenuStock(@PathVariable("menuId") UUID menuId);
}
