package com.example.cloudfour.modulecommon.converter;

import com.example.cloudfour.modulecommon.messaging.order.OrderCommands;

import java.time.Instant;
import java.util.UUID;

public class OrderCommandConverter {

    public static OrderCommands.ApproveOrder toApproveOrderCommand(String orderId, String userId, String storeId) {
        return OrderCommands.ApproveOrder.builder()
                .orderId(UUID.fromString(orderId))
                .userId(UUID.fromString(userId))
                .storeId(UUID.fromString(storeId))
                .requestedAt(Instant.now())
                .build();
    }

    public static OrderCommands.CancelOrder toCancelOrderCommand(String orderId, String userId, String storeId, String reason) {
        return OrderCommands.CancelOrder.builder()
                .orderId(UUID.fromString(orderId))
                .userId(UUID.fromString(userId))
                .storeId(UUID.fromString(storeId))
                .reason(reason)
                .requestedAt(Instant.now())
                .build();
    }
}
