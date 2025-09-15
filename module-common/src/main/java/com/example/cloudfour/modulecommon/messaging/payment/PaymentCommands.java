package com.example.cloudfour.modulecommon.messaging.payment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentCommands {
    
    @Value
    @Builder
    @Jacksonized
    public static class CreatePayment {
        UUID orderId;
        UUID userId;
        UUID storeId;
        Integer amount;
        String paymentMethod;
        Instant requestedAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class CancelPayment {
        UUID orderId;
        UUID userId;
        String paymentKey;
        String reason;
        Instant requestedAt;
    }
}