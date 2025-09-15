package com.example.cloudfour.modulecommon.saga.listener;

import com.example.cloudfour.modulecommon.converter.SagaDataConverter;
import com.example.cloudfour.modulecommon.messaging.SagaAwareDLQHandler;
import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MessageConsumer;
import com.example.cloudfour.modulecommon.messaging.inventory.InventoryEvents;
import com.example.cloudfour.modulecommon.messaging.order.OrderEvents;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import com.example.cloudfour.modulecommon.saga.service.SagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SagaEventListener {
    
    private final SagaOrchestrator sagaOrchestrator;
    private final MessageConsumer messageConsumer;
    private final ObjectMapper objectMapper;
    private final OutboxService outboxService;
    private final SagaAwareDLQHandler sagaAwareDLQHandler;

    @KafkaListener(topics = "${kafka.topics.orderEvents:order.events.v1}", 
                   groupId = "order-saga-orchestrator")
    public void handleOrderCreated(
            @Payload Envelope<Object> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();
            String eventType = envelope.getMeta().getType();

            if ("OrderCanceled".equals(eventType)) {
                log.info("주문 취소 이벤트 무시: orderId={}", envelope.getMeta().getSagaId());
                acknowledgment.acknowledge();
                return;
            }
            
            OrderEvents.OrderCreated event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, OrderEvents.OrderCreated.class);
            } else if (payload instanceof OrderEvents.OrderCreated) {
                event = (OrderEvents.OrderCreated) payload;
            } else {
                log.warn("OrderCreated 이벤트가 아닙니다: {}", payload.getClass().getSimpleName());
                acknowledgment.acknowledge();
                return;
            }
            String orderId = event.getOrderId().toString();
            String userId = event.getUserId().toString();
            String storeId = event.getStoreId().toString();
            
            log.info("주문 생성 이벤트 처리: orderId={}, userId={}, storeId={}", orderId, userId, storeId);

            String sagaData = SagaDataConverter.createSagaDataWithOrderItems(event);

            sagaOrchestrator.startOrderSaga(orderId, userId, storeId, sagaData);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("주문 생성 이벤트 처리 실패: error={}", e.getMessage(), e);

            handleMessageFailure(topic, key, envelope, e, acknowledgment);
        }
    }

    @KafkaListener(topics = "${kafka.topics.inventoryEvents:inventory.events.v1}", 
                   groupId = "order-saga-orchestrator")
    public void handleInventoryEvent(
            @Payload Envelope<Object> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();
            String eventType = envelope.getMeta().getType();
            
            if ("InventoryReserved".equals(eventType)) {
                handleInventoryReserved(payload, envelope, acknowledgment);
            } else if ("InventoryReservationFailed".equals(eventType)) {
                handleInventoryReservationFailed(payload, envelope, acknowledgment);
            } else if ("InventoryCommitted".equals(eventType)) {
                handleInventoryCommitted(payload, envelope, acknowledgment);
            } else if ("InventoryCommitFailed".equals(eventType)) {
                handleInventoryCommitFailed(payload, envelope, acknowledgment);
            } else if ("InventoryReleased".equals(eventType)) {
                handleInventoryReleased(payload, envelope, acknowledgment);
            } else {
                log.warn("알 수 없는 재고 이벤트 타입: {}", eventType);
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("재고 이벤트 처리 실패: error={}", e.getMessage(), e);

            handleMessageFailure(topic, key, envelope, e, acknowledgment);
        }
    }
    
    private void handleInventoryReserved(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            InventoryEvents.InventoryReserved event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, InventoryEvents.InventoryReserved.class);
            } else {
                event = (InventoryEvents.InventoryReserved) payload;
            }
            String orderId = event.getOrderId().toString();
            
            log.info("재고 예약 성공 이벤트 처리: orderId={}", orderId);
            
            sagaOrchestrator.handleInventoryReserved(orderId, envelope.getMeta().getMsgId());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("재고 예약 성공 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void handleInventoryReservationFailed(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            InventoryEvents.InventoryReservationFailed event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, InventoryEvents.InventoryReservationFailed.class);
            } else {
                event = (InventoryEvents.InventoryReservationFailed) payload;
            }
            String orderId = event.getOrderId().toString();
            String reason = event.getReason();
            
            log.info("재고 예약 실패 이벤트 처리: orderId={}, reason={}", orderId, reason);
            
            sagaOrchestrator.handleInventoryReservationFailed(orderId, envelope.getMeta().getMsgId(), reason);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("재고 예약 실패 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.paymentEvents:payment.events.v1}", 
                   groupId = "order-saga-orchestrator")
    public void handlePaymentEvent(
            @Payload Envelope<Object> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();
            String eventType = envelope.getMeta().getType();
            
            if ("PaymentCreated".equals(eventType)) {
                handlePaymentCreated(payload, envelope, acknowledgment);
            } else if ("PaymentAuthorized".equals(eventType)) {
                handlePaymentAuthorized(payload, envelope, acknowledgment);
            } else if ("PaymentFailed".equals(eventType)) {
                handlePaymentFailed(payload, envelope, acknowledgment);
            } else {
                log.warn("알 수 없는 결제 이벤트 타입: {}", eventType);
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("결제 이벤트 처리 실패: error={}", e.getMessage(), e);

            handleMessageFailure(topic, key, envelope, e, acknowledgment);
        }
    }
    
    private void handlePaymentCreated(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            PaymentEvents.PaymentCreated event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, PaymentEvents.PaymentCreated.class);
            } else {
                event = (PaymentEvents.PaymentCreated) payload;
            }
            String orderId = event.getOrderId().toString();
            
            log.info("결제 정보 생성 이벤트 처리: orderId={}", orderId);

            log.debug("결제 정보가 생성되었습니다: orderId={}, userId={}, storeId={}, amount={}", 
                    orderId, event.getUserId(), event.getStoreId(), event.getAmount());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("결제 정보 생성 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void handlePaymentAuthorized(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            PaymentEvents.PaymentAuthorized event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, PaymentEvents.PaymentAuthorized.class);
            } else {
                event = (PaymentEvents.PaymentAuthorized) payload;
            }
            String orderId = event.getOrderId().toString();
            
            log.info("결제 승인 성공 이벤트 처리: orderId={}", orderId);
            
            sagaOrchestrator.handlePaymentAuthorized(orderId, envelope.getMeta().getMsgId());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("결제 승인 성공 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void handlePaymentFailed(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            PaymentEvents.PaymentFailed event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, PaymentEvents.PaymentFailed.class);
            } else {
                event = (PaymentEvents.PaymentFailed) payload;
            }
            String orderId = event.getOrderId().toString();
            String reason = event.getReason();
            
            log.info("결제 실패 이벤트 처리: orderId={}, reason={}", orderId, reason);
            
            sagaOrchestrator.handlePaymentFailed(orderId, envelope.getMeta().getMsgId(), reason);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("결제 실패 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void handleInventoryCommitted(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            InventoryEvents.InventoryCommitted event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, InventoryEvents.InventoryCommitted.class);
            } else {
                event = (InventoryEvents.InventoryCommitted) payload;
            }
            String orderId = event.getOrderId().toString();
            
            log.info("재고 commit 성공 이벤트 처리: orderId={}", orderId);

            publishOrderApprovedEvent(orderId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("재고 commit 성공 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void handleInventoryCommitFailed(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            InventoryEvents.InventoryCommitFailed event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, InventoryEvents.InventoryCommitFailed.class);
            } else {
                event = (InventoryEvents.InventoryCommitFailed) payload;
            }
            String orderId = event.getOrderId().toString();
            String reason = event.getReason();
            
            log.info("재고 commit 실패 이벤트 처리: orderId={}, reason={}", orderId, reason);

            publishOrderCanceledEvent(orderId, reason);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("재고 commit 실패 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void handleInventoryReleased(Object payload, Envelope<Object> envelope, Acknowledgment acknowledgment) {
        try {
            InventoryEvents.InventoryReleased event;
            
            if (payload instanceof LinkedHashMap) {
                event = objectMapper.convertValue(payload, InventoryEvents.InventoryReleased.class);
            } else {
                event = (InventoryEvents.InventoryReleased) payload;
            }
            String orderId = event.getOrderId().toString();
            
            log.info("재고 해제 완료 이벤트 처리: orderId={}", orderId);

            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("재고 해제 완료 이벤트 처리 실패: error={}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private void publishOrderApprovedEvent(String orderId) {
        try {
            OrderEvents.OrderApproved event = OrderEvents.OrderApproved.builder()
                    .orderId(UUID.fromString(orderId))
                    .approvedAt(Instant.now())
                    .build();
            
            outboxService.saveEvent(
                    orderId,
                    "Order",
                    "OrderApproved",
                    event,
                    "order.events.v1",
                    orderId
            );
            
            log.info("OrderApproved 이벤트 발행 완료: orderId={}", orderId);
            
        } catch (Exception e) {
            log.error("OrderApproved 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    private void publishOrderCanceledEvent(String orderId, String reason) {
        try {
            OrderEvents.OrderCanceled event = OrderEvents.OrderCanceled.builder()
                    .orderId(UUID.fromString(orderId))
                    .reason(reason)
                    .canceledAt(Instant.now())
                    .build();
            
            outboxService.saveEvent(
                    orderId,
                    "Order",
                    "OrderCanceled",
                    event,
                    "order.events.v1",
                    orderId
            );
            
            log.info("OrderCanceled 이벤트 발행 완료: orderId={}, reason={}", orderId, reason);
            
        } catch (Exception e) {
            log.error("OrderCanceled 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    private void handleMessageFailure(String topic, String key, Envelope<Object> envelope, 
                                    Exception error, Acknowledgment acknowledgment) {
        sagaAwareDLQHandler.handleSagaFailure(topic, key, envelope, error, acknowledgment);
    }
}
