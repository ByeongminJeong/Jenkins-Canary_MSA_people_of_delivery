package com.example.cloudfour.modulecommon.messaging.inventory;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InventoryEvents {
    
    @Value
    @Builder
    @Jacksonized
    public static class InventoryReserved {
        UUID orderId;
        UUID storeId;
        List<ReservedItem> reservedItems;
        Instant reservedAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class ReservedItem {
            UUID menuId;
            String menuName;
            Integer quantity;
            Integer reservedQuantity;
        }
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class InventoryReservationFailed {
        UUID orderId;
        UUID storeId;
        String reason;
        List<FailedItem> failedItems;
        Instant failedAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class FailedItem {
            UUID menuId;
            String menuName;
            Integer requestedQuantity;
            Integer availableQuantity;
        }
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class InventoryReleased {
        UUID orderId;
        UUID storeId;
        List<ReleasedItem> releasedItems;
        Instant releasedAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class ReleasedItem {
            UUID menuId;
            String menuName;
            Integer quantity;
        }
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class InventoryCommitted {
        UUID orderId;
        UUID storeId;
        List<CommittedItem> committedItems;
        Instant committedAt;
        
        @Value
        @Builder
        @Jacksonized
        public static class CommittedItem {
            UUID menuId;
            String menuName;
            Integer quantity;
        }
    }
    
    @Value
    @Builder
    @Jacksonized
    public static class InventoryCommitFailed {
        UUID orderId;
        UUID storeId;
        String reason;
        Instant failedAt;
    }
}