package com.example.cloudfour.storeservice.domain.menu.dto;

import com.example.cloudfour.storeservice.domain.common.enums.PaymentStatus;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PaymentEvent {

    @Value
    @Builder
    @Jacksonized
    public static class PaymentCompletedEvent{
        UUID orderId;
        UUID userId;
        UUID paymentId;
        Long quantity;
        List<OrderItem> orderItems;
        LocalDateTime completedAt;
        PaymentStatus status;

        @Value
        @Builder
        @Jacksonized
        public static class OrderItem {
            UUID menuId;
            UUID stockId;
            String menuName;
            Long quantity;
            Integer price;
        }
    }

    @Value
    @Builder
    @Jacksonized
    public static class PaymentFailedEvent{
        UUID orderId;
        UUID userId;
        UUID paymentId;
        LocalDateTime failedAt;
    }

    @Value
    @Builder
    @Jacksonized
    public static class PaymentCanceledEvent{
        UUID orderId;
        LocalDateTime cancelledAt;
    }
}
