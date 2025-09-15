package com.example.cloudfour.modulecommon.messaging.inventory;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InventoryCommands {
    
    @Value
    @Builder
    @Jacksonized
    public static class ReserveInventory {
        UUID orderId;
        UUID storeId;
        List<ReserveItem> items;
        Instant requestedAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class ReserveItem {
            UUID menuId;
            String menuName;
            Integer quantity;
        }
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class ReleaseInventory {
        UUID orderId;
        UUID storeId;
        List<ReleaseItem> items;
        Instant requestedAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class ReleaseItem {
            UUID menuId;
            String menuName;
            Integer quantity;
        }
    }
}


