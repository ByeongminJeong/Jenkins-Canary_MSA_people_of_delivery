package com.example.cloudfour.storeservice.domain.menu.service;

import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MessageConsumer;
import com.example.cloudfour.modulecommon.messaging.SagaAwareDLQHandler;
import com.example.cloudfour.modulecommon.messaging.inventory.InventoryCommands;
import com.example.cloudfour.modulecommon.messaging.inventory.InventoryEvents;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.storeservice.domain.menu.service.command.StockCommandService;
import com.example.cloudfour.storeservice.domain.menu.service.RedisInventoryService;
import com.example.cloudfour.storeservice.domain.menu.service.event.InventoryEventService;
import com.example.cloudfour.storeservice.domain.menu.dto.PaymentEvent;
import com.example.cloudfour.storeservice.domain.menu.exception.StockException;
import com.example.cloudfour.storeservice.domain.menu.exception.StockErrorCode;
import com.example.cloudfour.storeservice.domain.menu.repository.MenuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCommandHandler {
    
    private final StockCommandService stockCommandService;
    private final RedisInventoryService redisInventoryService;
    private final InventoryEventService inventoryEventService;
    private final MenuRepository menuRepository;
    private final MessageConsumer messageConsumer;
    private final SagaAwareDLQHandler sagaAwareDLQHandler;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topics.inventoryEvents:inventory.events.v1}")
    private String inventoryEventsTopic;

    @KafkaListener(topics = "${kafka.topics.inventoryCommands:inventory.commands.v1}", 
                   groupId = "inventory-command-handler")
    public void handleInventoryCommand(
            @Payload Envelope<?> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();

            if (payload instanceof Map) {
                payload = convertLinkedHashMapToCommand((Map<String, Object>) payload, envelope.getMeta().getType());
            }
            
                        if (payload instanceof InventoryCommands.ReserveInventory) {
                            handleReserveInventory((InventoryCommands.ReserveInventory) payload);
                            acknowledgment.acknowledge();
                        } else if (payload instanceof InventoryCommands.ReleaseInventory) {
                            handleReleaseInventory((InventoryCommands.ReleaseInventory) payload);
                            acknowledgment.acknowledge();
                        } else {
                            log.warn("알 수 없는 인벤토리 커맨드 타입: {}", payload.getClass().getSimpleName());
                            acknowledgment.acknowledge();
                        }
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("인벤토리 커맨드 처리 실패: error={}", e.getMessage(), e);
            
            sagaAwareDLQHandler.handleSagaFailure(topic, key, (Envelope<Object>) envelope, e, acknowledgment);
        }
    }

    @KafkaListener(topics = "${kafka.topics.paymentEvents:payment.events.v1}", 
                   groupId = "inventory-payment-event-handler")
    public void handlePaymentEvent(
            @Payload Envelope<?> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();
            String eventType = envelope.getMeta().getType();

            if ("PaymentCompleted".equals(eventType)) {
                handleCommitInventory(payload);
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("결제 이벤트 처리 실패: error={}", e.getMessage(), e);
            
            sagaAwareDLQHandler.handleSagaFailure(topic, key, (Envelope<Object>) envelope, e, acknowledgment);
        }
    }
    
    private Object convertLinkedHashMapToCommand(Map<String, Object> map, String type) {
        try {
            if ("ReserveInventory".equals(type)) {
                return objectMapper.convertValue(map, InventoryCommands.ReserveInventory.class);
            } else if ("ReleaseInventory".equals(type)) {
                return objectMapper.convertValue(map, InventoryCommands.ReleaseInventory.class);
            }
            return map;
        } catch (Exception e) {
            log.error("커맨드 변환 실패: type={}, error={}", type, e.getMessage(), e);
            return map;
        }
    }
    
    private void handleReserveInventory(InventoryCommands.ReserveInventory command) {
        log.info("재고 예약 처리 시작: orderId={}, storeId={}", command.getOrderId(), command.getStoreId());
        
        try {
            for (InventoryCommands.ReserveInventory.ReserveItem item : command.getItems()) {
                var result = redisInventoryService.reserveStock(
                    item.getMenuId(), 
                    item.getQuantity().longValue(), 
                    command.getOrderId()
                );
                
                if (!result.isSuccess()) {
                    log.warn("재고 예약 실패: menuId={}, quantity={}, reason={}", 
                            item.getMenuId(), item.getQuantity(), result.getMessage());

                    inventoryEventService.publishInventoryReservationFailed(
                        command.getOrderId(), 
                        command.getStoreId(), 
                        List.of(InventoryEvents.InventoryReservationFailed.FailedItem.builder()
                            .menuId(item.getMenuId())
                            .menuName(getMenuName(item.getMenuId()))
                            .requestedQuantity(item.getQuantity())
                            .availableQuantity(result.getRemainingStock().intValue())
                            .build())
                    );
                    return;
                }
            }

            List<InventoryEvents.InventoryReserved.ReservedItem> reservedItems = command.getItems().stream()
                .map(item -> InventoryEvents.InventoryReserved.ReservedItem.builder()
                    .menuId(item.getMenuId())
                    .menuName(getMenuName(item.getMenuId()))
                    .quantity(item.getQuantity().intValue())
                    .reservedQuantity(item.getQuantity().intValue())
                    .build())
                .toList();
                
            inventoryEventService.publishInventoryReserved(
                command.getOrderId(), 
                command.getStoreId(), 
                reservedItems
            );
            
            log.info("재고 예약 완료: orderId={}, storeId={}", command.getOrderId(), command.getStoreId());
            
        } catch (Exception e) {
            log.error("재고 예약 처리 중 오류: orderId={}, error={}", command.getOrderId(), e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
    
    private void handleReleaseInventory(InventoryCommands.ReleaseInventory command) {
        log.info("재고 해제 처리 시작: orderId={}", command.getOrderId());
        
        try {
            redisInventoryService.releaseReservation(command.getOrderId());
            
            log.info("재고 해제 완료: orderId={}", command.getOrderId());
            
        } catch (Exception e) {
            log.error("재고 해제 처리 중 오류: orderId={}, error={}", command.getOrderId(), e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
    
    private void handleCommitInventory(Object event) {
        log.info("재고 commit 처리 시작: event={}", event);
        
        try {
            if (event instanceof PaymentEvent.PaymentCompletedEvent paymentEvent) {
                List<PaymentEvent.PaymentCompletedEvent.OrderItem> orderItems = paymentEvent.getOrderItems();
 
                boolean success = stockCommandService.decreaseListStock(orderItems);
                
                if (success) {
                    List<InventoryEvents.InventoryCommitted.CommittedItem> committedItems = orderItems.stream()
                        .map(item -> InventoryEvents.InventoryCommitted.CommittedItem.builder()
                            .menuId(item.getMenuId())
                            .menuName(getMenuName(item.getMenuId()))
                            .quantity(item.getQuantity().intValue())
                            .build())
                        .toList();

                    UUID storeId = orderItems.isEmpty() ? null : getStoreId(orderItems.get(0).getMenuId());
                    
                    inventoryEventService.publishInventoryCommitted(
                        paymentEvent.getOrderId(),
                        storeId,
                        committedItems
                    );
                    
                    log.info("재고 commit 완료: orderId={}, paymentId={}", 
                            paymentEvent.getOrderId(), paymentEvent.getPaymentId());
                } else {
                    UUID storeId = orderItems.isEmpty() ? null : getStoreId(orderItems.get(0).getMenuId());
                    
                    inventoryEventService.publishInventoryCommitFailed(
                        paymentEvent.getOrderId(),
                        storeId,
                        "재고 차감 실패"
                    );
                    
                    log.warn("재고 commit 실패: orderId={}, paymentId={}", 
                            paymentEvent.getOrderId(), paymentEvent.getPaymentId());
                }
            } else {
                log.warn("알 수 없는 이벤트 타입: {}", event.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            log.error("재고 commit 처리 중 오류: event={}, error={}", event, e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
    
    private String getMenuName(UUID menuId) {
        try {
            return menuRepository.findById(menuId)
                .map(menu -> menu.getName())
                .orElse("알 수 없는 메뉴");
        } catch (Exception e) {
            log.warn("메뉴명 조회 실패: menuId={}, error={}", menuId, e.getMessage());
            return "알 수 없는 메뉴";
        }
    }
    
    private UUID getStoreId(UUID menuId) {
        try {
            return menuRepository.findById(menuId)
                .map(menu -> menu.getStore().getId())
                .orElse(null);
        } catch (Exception e) {
            log.warn("가게 ID 조회 실패: menuId={}, error={}", menuId, e.getMessage());
            return null;
        }
    }
}