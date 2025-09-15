package com.example.cloudfour.storeservice.domain.menu.service.event;

import com.example.cloudfour.modulecommon.messaging.inventory.InventoryEvents;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import com.example.cloudfour.storeservice.domain.menu.converter.InventoryEventConverter;
import com.example.cloudfour.storeservice.domain.menu.exception.StockException;
import com.example.cloudfour.storeservice.domain.menu.exception.StockErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryEventService {
    
    private final OutboxService outboxService;
    
    @Value("${kafka.topics.inventoryEvents:inventory.events.v1}")
    private String inventoryEventsTopic;

    @Transactional
    public void publishInventoryReserved(UUID orderId, UUID storeId, List<InventoryEvents.InventoryReserved.ReservedItem> reservedItems) {
        try {
            InventoryEvents.InventoryReserved event = InventoryEventConverter.createInventoryReservedEvent(
                    orderId, storeId, reservedItems);
            
            outboxService.saveEvent(
                    orderId.toString(),
                    "Inventory",
                    "InventoryReserved",
                    event,
                    inventoryEventsTopic,
                    orderId.toString()
            );
            
            log.info("InventoryReserved 이벤트 발행 완료: orderId={}, storeId={}", orderId, storeId);
            
        } catch (Exception e) {
            log.error("InventoryReserved 이벤트 발행 실패: orderId={}, error={}", 
                    orderId, e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
    
    @Transactional
    public void publishInventoryReservationFailed(UUID orderId, UUID storeId, List<InventoryEvents.InventoryReservationFailed.FailedItem> failedItems) {
        try {
            InventoryEvents.InventoryReservationFailed event = InventoryEventConverter.createInventoryReservationFailedEvent(
                    orderId, storeId, "재고 부족", failedItems);
            
            outboxService.saveEvent(
                    orderId.toString(),
                    "Inventory",
                    "InventoryReservationFailed",
                    event,
                    inventoryEventsTopic,
                    orderId.toString()
            );
            
            log.info("InventoryReservationFailed 이벤트 발행 완료: orderId={}, storeId={}", orderId, storeId);
            
        } catch (Exception e) {
            log.error("InventoryReservationFailed 이벤트 발행 실패: orderId={}, error={}", 
                    orderId, e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
    
    @Transactional
    public void publishInventoryCommitted(UUID orderId, UUID storeId, List<InventoryEvents.InventoryCommitted.CommittedItem> committedItems) {
        try {
            InventoryEvents.InventoryCommitted event = InventoryEventConverter.createInventoryCommittedEvent(
                    orderId, storeId, committedItems);
            
            outboxService.saveEvent(
                    orderId.toString(),
                    "Inventory",
                    "InventoryCommitted",
                    event,
                    inventoryEventsTopic,
                    orderId.toString()
            );
            
            log.info("InventoryCommitted 이벤트 발행 완료: orderId={}, storeId={}", orderId, storeId);
            
        } catch (Exception e) {
            log.error("InventoryCommitted 이벤트 발행 실패: orderId={}, error={}", 
                    orderId, e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
    
    @Transactional
    public void publishInventoryCommitFailed(UUID orderId, UUID storeId, String reason) {
        try {
            InventoryEvents.InventoryCommitFailed event = InventoryEventConverter.createInventoryCommitFailedEvent(
                    orderId, storeId, reason);
            
            outboxService.saveEvent(
                    orderId.toString(),
                    "Inventory",
                    "InventoryCommitFailed",
                    event,
                    inventoryEventsTopic,
                    orderId.toString()
            );
            
            log.info("InventoryCommitFailed 이벤트 발행 완료: orderId={}, storeId={}, reason={}", 
                    orderId, storeId, reason);
            
        } catch (Exception e) {
            log.error("InventoryCommitFailed 이벤트 발행 실패: orderId={}, error={}", 
                    orderId, e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
}
