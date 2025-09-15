package com.example.cloudfour.storeservice.domain.menu.converter;

import com.example.cloudfour.modulecommon.messaging.inventory.InventoryEvents;
import com.example.cloudfour.storeservice.domain.menu.service.command.StockCommandService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


public class InventoryEventConverter {

    private InventoryEventConverter() {
        throw new UnsupportedOperationException("유틸리티 클래스를 인스턴스화할 수 없습니다");
    }

    public static InventoryEvents.InventoryReserved.ReservedItem createReservedItem(
            UUID menuId, String menuName, Integer quantity) {
        return InventoryEvents.InventoryReserved.ReservedItem.builder()
                .menuId(menuId)
                .menuName(menuName)
                .quantity(quantity)
                .build();
    }

    public static InventoryEvents.InventoryReservationFailed.FailedItem createFailedItem(
            UUID menuId, String menuName, Integer requestedQuantity, Integer availableQuantity) {
        return InventoryEvents.InventoryReservationFailed.FailedItem.builder()
                .menuId(menuId)
                .menuName(menuName)
                .requestedQuantity(requestedQuantity)
                .availableQuantity(availableQuantity)
                .build();
    }

    public static InventoryEvents.InventoryReleased.ReleasedItem createReleasedItem(
            UUID menuId, String menuName, Integer quantity) {
        return InventoryEvents.InventoryReleased.ReleasedItem.builder()
                .menuId(menuId)
                .menuName(menuName)
                .quantity(quantity)
                .build();
    }

    public static InventoryEvents.InventoryCommitted.CommittedItem createCommittedItem(
            UUID menuId, String menuName, Integer quantity) {
        return InventoryEvents.InventoryCommitted.CommittedItem.builder()
                .menuId(menuId)
                .menuName(menuName)
                .quantity(quantity)
                .build();
    }


    public static InventoryEvents.InventoryReserved createInventoryReservedEvent(
            UUID orderId, UUID storeId, List<InventoryEvents.InventoryReserved.ReservedItem> reservedItems) {
        return InventoryEvents.InventoryReserved.builder()
                .orderId(orderId)
                .storeId(storeId)
                .reservedItems(reservedItems)
                .reservedAt(Instant.now())
                .build();
    }

    public static InventoryEvents.InventoryReservationFailed createInventoryReservationFailedEvent(
            UUID orderId, UUID storeId, String reason, List<InventoryEvents.InventoryReservationFailed.FailedItem> failedItems) {
        return InventoryEvents.InventoryReservationFailed.builder()
                .orderId(orderId)
                .storeId(storeId)
                .reason(reason)
                .failedItems(failedItems)
                .failedAt(Instant.now())
                .build();
    }

    public static InventoryEvents.InventoryReleased createInventoryReleasedEvent(
            UUID orderId, UUID storeId, List<InventoryEvents.InventoryReleased.ReleasedItem> releasedItems) {
        return InventoryEvents.InventoryReleased.builder()
                .orderId(orderId)
                .storeId(storeId)
                .releasedItems(releasedItems)
                .releasedAt(Instant.now())
                .build();
    }


    public static InventoryEvents.InventoryCommitted createInventoryCommittedEvent(
            UUID orderId, UUID storeId, List<InventoryEvents.InventoryCommitted.CommittedItem> committedItems) {
        return InventoryEvents.InventoryCommitted.builder()
                .orderId(orderId)
                .storeId(storeId)
                .committedItems(committedItems)
                .committedAt(Instant.now())
                .build();
    }

    public static InventoryEvents.InventoryCommitFailed createInventoryCommitFailedEvent(
            UUID orderId, UUID storeId, String reason) {
        return InventoryEvents.InventoryCommitFailed.builder()
                .orderId(orderId)
                .storeId(storeId)
                .reason(reason)
                .failedAt(Instant.now())
                .build();
    }
}
