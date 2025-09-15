package com.example.cloudfour.modulecommon.messaging.order;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderEvents {
    
    @Value
    @Builder
    @Jacksonized
    public static class OrderCreated {
        UUID orderId;
        UUID userId;
        UUID storeId;
        BigDecimal totalAmount;
        String orderStatus;
        String deliveryAddress;
        List<OrderItem> orderItems;
        Instant createdAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class OrderItem {
            UUID menuId;
            Integer quantity;
            BigDecimal price;
            List<OrderItemOption> options;
        }
        
        @Value
        @Builder
        @Jacksonized
        public static class OrderItemOption {
            UUID optionId;
            String optionName;
            BigDecimal optionPrice;
        }
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class OrderApproved {
        UUID orderId;
        UUID userId;
        UUID storeId;
        Instant approvedAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class OrderCanceled {
        UUID orderId;
        UUID userId;
        UUID storeId;
        String reason;
        Instant canceledAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class OrderFailed {
        UUID orderId;
        UUID userId;
        UUID storeId;
        String reason;
        Instant failedAt;
    }
}