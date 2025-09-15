package com.example.cloudfour.modulecommon.converter;

import com.example.cloudfour.modulecommon.messaging.inventory.InventoryCommands;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InventoryCommandConverter {
    public static InventoryCommands.ReserveInventory toReserveInventoryCommand(String orderId, String storeId, List<InventoryCommands.ReserveInventory.ReserveItem> items) {
        return InventoryCommands.ReserveInventory.builder()
                .orderId(UUID.fromString(orderId))
                .storeId(UUID.fromString(storeId))
                .items(items)
                .requestedAt(Instant.now())
                .build();
    }

    public static InventoryCommands.ReleaseInventory toReleaseInventoryCommand(String orderId, String storeId, List<InventoryCommands.ReleaseInventory.ReleaseItem> items) {
        return InventoryCommands.ReleaseInventory.builder()
                .orderId(UUID.fromString(orderId))
                .storeId(UUID.fromString(storeId))
                .items(items)
                .requestedAt(Instant.now())
                .build();
    }
}
