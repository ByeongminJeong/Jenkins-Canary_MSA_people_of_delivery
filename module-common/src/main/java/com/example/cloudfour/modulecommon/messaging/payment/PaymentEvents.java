package com.example.cloudfour.modulecommon.messaging.payment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentEvents {
    
    @Value
    @Builder
    @Jacksonized
    public static class PaymentCreated {
        UUID orderId;
        UUID userId;
        UUID storeId;
        BigDecimal amount;
        String paymentMethod;
        String paymentStatus;
        Instant createdAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class PaymentAuthorized {
        UUID orderId;
        UUID userId;
        UUID storeId;
        String paymentKey;
        BigDecimal amount;
        String paymentMethod;
        Instant authorizedAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class PaymentFailed {
        UUID orderId;
        UUID userId;
        String reason;
        Instant failedAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class PaymentCanceled {
        UUID orderId;
        UUID userId;
        String paymentKey;
        String reason;
        Instant canceledAt;
    }
    
}