package com.example.cloudfour.storeservice.domain.menu.controller;

import com.example.cloudfour.storeservice.domain.menu.dto.StockResponseDTO;
import com.example.cloudfour.storeservice.domain.menu.service.command.StockCommandService;
import com.example.cloudfour.storeservice.domain.menu.service.query.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalStockController {

    private final StockQueryService stockQueryService;
    private final StockCommandService stockCommandService;

    @GetMapping("/menus/{menuId}/stock")
    public StockResponseDTO.StockCacheResponseDTO getMenuStock ( @PathVariable("menuId") UUID menuId ) {
        return stockQueryService.getMenuStock(menuId);
    }

    @GetMapping("/menus/{menuId}/stock/availability")
    public StockResponseDTO.StockAvailabilityResponseDTO getMenuStockAvailability(@PathVariable("menuId") UUID menuId) {
        return stockQueryService.getMenuStockAvailability(menuId);
    }
}
