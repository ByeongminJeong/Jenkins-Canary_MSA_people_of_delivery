package com.example.cloudfour.storeservice.domain.menu.dto;

import com.example.cloudfour.storeservice.domain.common.enums.OrderStatus;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class OrderEvent {
    @Value
    @Builder
    @Jacksonized
    public static class OrderCompletedEvent{
        UUID orderId;
        UUID userId;
        UUID paymentId;
        Long quantity;
        List<OrderItem> orderItems;
        LocalDateTime completedAt;
        OrderStatus status;

        @Value
        @Builder
        @Jacksonized
        public static class OrderItem {
            UUID menuId;
            String menuName;
            Long quantity;
            Integer price;
        }
    }
}
