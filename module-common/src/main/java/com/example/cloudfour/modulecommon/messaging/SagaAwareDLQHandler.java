package com.example.cloudfour.modulecommon.messaging;

import com.example.cloudfour.modulecommon.messaging.order.OrderEvents;
import com.example.cloudfour.modulecommon.messaging.inventory.InventoryCommands;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MsgMeta;
import com.example.cloudfour.modulecommon.messaging.DLQMessage;
import com.example.cloudfour.modulecommon.converter.MessageConverter;
import org.springframework.kafka.support.Acknowledgment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SagaAwareDLQHandler {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topics.orderEvents:order.events.v1}")
    private String orderEventsTopic;
    
    @Value("${kafka.topics.inventoryCommands:inventory.commands.v1}")
    private String inventoryCommandsTopic;
    
    @Value("${kafka.topics.paymentEvents:payment.events.v1}")
    private String paymentEventsTopic;
    
    public SagaAwareDLQHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    private static final List<String> COMPENSATABLE_EVENTS = Arrays.asList(
        "ReserveInventory",
        "PaymentAuthorized",
        "OrderCreated",
        "OrderApproved",
        "InventoryReserved",
        "InventoryCommitted"
    );

    public void handleSagaFailure(String topic, String key, Envelope<Object> envelope,
                                  Exception error, Acknowledgment acknowledgment) {
        try {
            String sagaId = envelope.getMeta().getSagaId();
            String eventType = envelope.getMeta().getType();
            String msgId = envelope.getMeta().getMsgId();
            
            log.error("Saga 실패 처리 시작: sagaId={}, eventType={}, topic={}, error={}", 
                    sagaId, eventType, topic, error.getMessage());

            log.info("사가 실패 처리: sagaId={}, eventType={}", sagaId, eventType);

            if (sagaId == null || sagaId.trim().isEmpty()) {
                log.warn("사가 ID가 없음: DLQ로 전송");
                sendToDLQ(topic, key, envelope, error, 0, "No saga ID");
                acknowledgment.acknowledge();
                return;
            }

            if (isCompensatableEvent(eventType)) {
                log.info("보상 트랜잭션 실행: sagaId={}, eventType={}", sagaId, eventType);
                executeCompensatingTransaction(sagaId, eventType, error, envelope);
            } else {
                log.warn("보상 불가능한 이벤트: sagaId={}, eventType={}, DLQ로 전송", sagaId, eventType);
                sendToDLQ(topic, key, envelope, error, 0, "Non-compensatable event");
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Saga 실패 처리 중 오류 발생: {}", e.getMessage(), e);
            try {
                sendToDLQ(topic, key, envelope, error, 0, "Compensation failed: " + e.getMessage());
                acknowledgment.acknowledge();
            } catch (Exception dlqError) {
                log.error("DLQ 전송도 실패: {}", dlqError.getMessage(), dlqError);
                acknowledgment.acknowledge();
            }
        }
    }

    private boolean isCompensatableEvent(String eventType) {
        return COMPENSATABLE_EVENTS.contains(eventType);
    }

    private void executeCompensatingTransaction(String sagaId, String eventType, 
                                               Exception error, Envelope<Object> envelope) {
        try {
            log.info("보상 트랜잭션 실행 시작: sagaId={}, eventType={}, error={}", 
                    sagaId, eventType, error.getMessage());

            Object originalPayload = envelope.getPayload();
            String userId = extractUserId(originalPayload);
            String storeId = extractStoreId(originalPayload);
            String paymentKey = extractPaymentKey(originalPayload);
            String originalMsgId = envelope.getMeta().getMsgId();
            
            switch (eventType) {
                case "ReserveInventory":
                case "InventoryReserved":
                    publishInventoryReleaseEvent(sagaId, storeId, originalMsgId);
                    break;
                    
                case "PaymentAuthorized":
                    publishPaymentCanceledEvent(sagaId, userId, paymentKey, "Saga compensation: " + error.getMessage(), originalMsgId);
                    break;
                    
                case "OrderCreated":
                case "OrderApproved":
                    publishOrderCanceledEvent(sagaId, userId, storeId, "Saga compensation: " + error.getMessage(), originalMsgId);
                    break;
                    
                case "InventoryCommitted":
                    publishInventoryReleaseEvent(sagaId, storeId, originalMsgId);
                    break;
                    
                default:
                    log.warn("보상 불가능한 이벤트 타입: sagaId={}, eventType={}", sagaId, eventType);
                    throw new IllegalArgumentException("보상 불가능한 이벤트 타입: " + eventType);
            }
            
            log.info("보상 트랜잭션 실행 완료: sagaId={}, eventType={}", sagaId, eventType);
            
        } catch (Exception e) {
            log.error("보상 트랜잭션 실행 실패: sagaId={}, eventType={}, error={}", 
                    sagaId, eventType, e.getMessage(), e);
            throw e;
        }
    }

    private String extractUserId(Object payload) {
        try {
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                Object userId = map.get("userId");
                return userId != null ? userId.toString() : null;
            }
            return null;
        } catch (Exception e) {
            log.warn("userId 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private String extractStoreId(Object payload) {
        try {
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                Object storeId = map.get("storeId");
                return storeId != null ? storeId.toString() : null;
            }
            return null;
        } catch (Exception e) {
            log.warn("storeId 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private String extractPaymentKey(Object payload) {
        try {
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                Object paymentKey = map.get("paymentKey");
                return paymentKey != null ? paymentKey.toString() : null;
            }
            return null;
        } catch (Exception e) {
            log.warn("paymentKey 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private void publishOrderCanceledEvent(String orderId, String userId, String storeId, String reason, String originalMsgId) {
        try {
            OrderEvents.OrderCanceled event = OrderEvents.OrderCanceled.builder()
                    .orderId(UUID.fromString(orderId))
                    .userId(userId != null ? UUID.fromString(userId) : null)
                    .storeId(storeId != null ? UUID.fromString(storeId) : null)
                    .reason(reason)
                    .canceledAt(Instant.now())
                    .build();
            
            MsgMeta meta = MsgMeta.builder()
                    .msgId(originalMsgId)
                    .sagaId(orderId)
                    .type("OrderCanceled")
                    .correlationId(orderId)
                    .build();
            Envelope<OrderEvents.OrderCanceled> envelope = MessageConverter.toEnvelope(meta, event);
            
            kafkaTemplate.send(orderEventsTopic, orderId, envelope);
            log.info("주문 취소 이벤트 발행: orderId={}, userId={}, storeId={}, reason={}", 
                    orderId, userId, storeId, reason);
            
        } catch (Exception e) {
            log.error("주문 취소 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw e;
        }
    }

    private void publishInventoryReleaseEvent(String orderId, String storeId, String originalMsgId) {
        try {
            InventoryCommands.ReleaseInventory command = InventoryCommands.ReleaseInventory.builder()
                    .orderId(UUID.fromString(orderId))
                    .storeId(storeId != null ? UUID.fromString(storeId) : null)
                    .build();
            
            MsgMeta meta = MsgMeta.builder()
                    .msgId(originalMsgId)
                    .sagaId(orderId)
                    .type("ReleaseInventory")
                    .correlationId(orderId)
                    .build();
            Envelope<InventoryCommands.ReleaseInventory> envelope = MessageConverter.toEnvelope(meta, command);
            
            kafkaTemplate.send(inventoryCommandsTopic, orderId, envelope);
            log.info("재고 해제 커맨드 발행: orderId={}, storeId={}", orderId, storeId);
            
        } catch (Exception e) {
            log.error("재고 해제 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw e;
        }
    }

    private void publishPaymentCanceledEvent(String orderId, String userId, String paymentKey, String reason, String originalMsgId) {
        try {
            PaymentEvents.PaymentCanceled event = PaymentEvents.PaymentCanceled.builder()
                    .orderId(UUID.fromString(orderId))
                    .userId(userId != null ? UUID.fromString(userId) : null)
                    .paymentKey(paymentKey)
                    .reason(reason)
                    .canceledAt(Instant.now())
                    .build();
            
            MsgMeta meta = MsgMeta.builder()
                    .msgId(originalMsgId)
                    .sagaId(orderId)
                    .type("PaymentCanceled")
                    .correlationId(orderId)
                    .build();
            Envelope<PaymentEvents.PaymentCanceled> envelope = MessageConverter.toEnvelope(meta, event);
            
            kafkaTemplate.send(paymentEventsTopic, orderId, envelope);
            log.info("결제 취소 이벤트 발행: orderId={}, userId={}, reason={}", orderId, userId, reason);
            
        } catch (Exception e) {
            log.error("결제 취소 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw e;
        }
    }

    private void sendToDLQ(String originalTopic, String key, Envelope<Object> envelope,
                          Exception error, int retryCount, String reason) {
        try {
            String dlqTopic = originalTopic.replace(".v1", ".dlq.v1");
            
            DLQMessage dlqMessage = DLQMessage.builder()
                    .originalTopic(originalTopic)
                    .originalKey(key)
                    .originalMessage(envelope)
                    .errorMessage(error.getMessage())
                    .errorClass(error.getClass().getSimpleName())
                    .retryCount(retryCount)
                    .failedAt(LocalDateTime.now())
                    .metadata(Map.of("reason", reason))
                    .build();
            
            MsgMeta meta = MsgMeta.builder()
                    .msgId(envelope.getMeta().getMsgId())
                    .sagaId(envelope.getMeta().getSagaId())
                    .type("DLQMessage")
                    .correlationId(envelope.getMeta().getCorrelationId())
                    .build();
            Envelope<DLQMessage> dlqEnvelope = MessageConverter.toEnvelope(meta, dlqMessage);
            
            kafkaTemplate.send(dlqTopic, key, dlqEnvelope);
            log.info("DLQ로 메시지 전송: originalTopic={}, dlqTopic={}, reason={}", 
                    originalTopic, dlqTopic, reason);
            
        } catch (Exception e) {
            log.error("DLQ 전송 실패: originalTopic={}, error={}", originalTopic, e.getMessage(), e);
            throw e;
        }
    }
}
