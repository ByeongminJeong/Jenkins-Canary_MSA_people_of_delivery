package com.example.cloudfour.modulecommon.messaging.order;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

public class OrderCommands {
    
    @Value
    @Builder
    @Jacksonized
    public static class ApproveOrder {
        UUID orderId;
        UUID userId;
        UUID storeId;
        Instant requestedAt;
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class CancelOrder {
        UUID orderId;
        UUID userId;
        UUID storeId;
        String reason;
        Instant requestedAt;
    }
}