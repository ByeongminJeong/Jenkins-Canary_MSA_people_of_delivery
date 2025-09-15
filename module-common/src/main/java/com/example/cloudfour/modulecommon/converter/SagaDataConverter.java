package com.example.cloudfour.modulecommon.converter;

import com.example.cloudfour.modulecommon.messaging.inventory.InventoryCommands;
import com.example.cloudfour.modulecommon.messaging.order.OrderEvents;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class SagaDataConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String createSagaDataWithOrderItems(OrderEvents.OrderCreated event) {
        try {
            Map<String, Object> sagaData = Map.of(
                    "userId", event.getUserId().toString(),
                    "storeId", event.getStoreId().toString(),
                    "totalAmount", event.getTotalAmount(),
                    "orderStatus", event.getOrderStatus(),
                    "deliveryAddress", event.getDeliveryAddress(),
                    "orderItems", event.getOrderItems().stream()
                            .map(item -> Map.<String, Object>of(
                                    "menuId", item.getMenuId().toString(),
                                    "quantity", item.getQuantity(),
                                    "price", item.getPrice(),
                                    "options", item.getOptions() != null ? item.getOptions().stream()
                                            .map(option -> Map.<String, Object>of(
                                                    "optionId", option.getOptionId().toString(),
                                                    "optionName", option.getOptionName(),
                                                    "optionPrice", option.getOptionPrice()
                                            ))
                                            .collect(Collectors.toList()) : Collections.emptyList()
                            ))
                            .collect(Collectors.toList())
            );
            
            return objectMapper.writeValueAsString(sagaData);
        } catch (JsonProcessingException e) {
            log.error("sagaData JSON 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("sagaData JSON 생성 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String extractUserIdFromSagaData(String sagaData) {
        try {
            Map<String, Object> data = objectMapper.readValue(sagaData, Map.class);
            Object userId = data.get("userId");
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            log.warn("sagaData에서 userId 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static String extractStoreIdFromSagaData(String sagaData) {
        try {
            Map<String, Object> data = objectMapper.readValue(sagaData, Map.class);
            Object storeId = data.get("storeId");
            return storeId != null ? storeId.toString() : null;
        } catch (Exception e) {
            log.warn("sagaData에서 storeId 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<InventoryCommands.ReserveInventory.ReserveItem> parseOrderItemsFromSagaData(String sagaData) {
        try {
            Map<String, Object> data = objectMapper.readValue(sagaData, Map.class);
            List<Map<String, Object>> orderItems = (List<Map<String, Object>>) data.get("orderItems");
            
            if (orderItems == null || orderItems.isEmpty()) {
                log.warn("sagaData에 주문 아이템 정보가 없습니다");
                return List.of();
            }
            
            return orderItems.stream()
                    .map(item -> InventoryCommands.ReserveInventory.ReserveItem.builder()
                            .menuId(UUID.fromString((String) item.get("menuId")))
                            .menuName("Menu-" + item.get("menuId")) // 임시 메뉴명 생성
                            .quantity((Integer) item.get("quantity"))
                            .build())
                    .toList();
                    
        } catch (Exception e) {
            log.error("sagaData에서 주문 아이템 파싱 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<InventoryCommands.ReleaseInventory.ReleaseItem> parseOrderItemsFromSagaDataForRelease(String sagaData) {
        try {
            Map<String, Object> data = objectMapper.readValue(sagaData, Map.class);
            List<Map<String, Object>> orderItems = (List<Map<String, Object>>) data.get("orderItems");
            
            if (orderItems == null || orderItems.isEmpty()) {
                log.warn("sagaData에서 주문 아이템 정보를 찾을 수 없습니다");
                return List.of();
            }
            
            return orderItems.stream()
                    .map(item -> InventoryCommands.ReleaseInventory.ReleaseItem.builder()
                            .menuId(UUID.fromString((String) item.get("menuId")))
                            .quantity((Integer) item.get("quantity"))
                            .build())
                    .toList();
                    
        } catch (Exception e) {
            log.error("sagaData에서 주문 아이템 정보 파싱 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
