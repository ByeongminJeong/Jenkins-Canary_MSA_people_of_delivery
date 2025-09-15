package com.example.cloudfour.modulecommon.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DLQListener {
    
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.topics.orderEventsDLQ:order.events.dlq.v1}",
        groupId = "dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void handleOrderEventDLQ(
            @Payload DLQMessage dlqMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.error("주문 이벤트 DLQ 처리: topic={}, key={}, originalTopic={}, retryCount={}, error={}", 
                    topic, key, dlqMessage.getOriginalTopic(), dlqMessage.getRetryCount(), 
                    dlqMessage.getErrorMessage());

            processDLQMessage(dlqMessage, "ORDER_EVENT");
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 실패: topic={}, key={}, error={}", 
                    topic, key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
        topics = "${kafka.topics.orderCommandsDLQ:order.commands.dlq.v1}",
        groupId = "dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void handleOrderCommandDLQ(
            @Payload DLQMessage dlqMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.error("주문 커맨드 DLQ 처리: topic={}, key={}, originalTopic={}, retryCount={}, error={}", 
                    topic, key, dlqMessage.getOriginalTopic(), dlqMessage.getRetryCount(), 
                    dlqMessage.getErrorMessage());
            
            processDLQMessage(dlqMessage, "ORDER_COMMAND");
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 실패: topic={}, key={}, error={}", 
                    topic, key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
        topics = "${kafka.topics.inventoryEventsDLQ:inventory.events.dlq.v1}",
        groupId = "dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void handleInventoryEventDLQ(
            @Payload DLQMessage dlqMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.error("재고 이벤트 DLQ 처리: topic={}, key={}, originalTopic={}, retryCount={}, error={}", 
                    topic, key, dlqMessage.getOriginalTopic(), dlqMessage.getRetryCount(), 
                    dlqMessage.getErrorMessage());
            
            processDLQMessage(dlqMessage, "INVENTORY_EVENT");
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 실패: topic={}, key={}, error={}", 
                    topic, key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
        topics = "${kafka.topics.paymentEventsDLQ:payment.events.dlq.v1}",
        groupId = "dlq-processor",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void handlePaymentEventDLQ(
            @Payload DLQMessage dlqMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.error("결제 이벤트 DLQ 처리: topic={}, key={}, originalTopic={}, retryCount={}, error={}", 
                    topic, key, dlqMessage.getOriginalTopic(), dlqMessage.getRetryCount(), 
                    dlqMessage.getErrorMessage());
            
            processDLQMessage(dlqMessage, "PAYMENT_EVENT");
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 실패: topic={}, key={}, error={}", 
                    topic, key, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private void processDLQMessage(DLQMessage dlqMessage, String messageType) {
        try {
            log.error("DLQ 메시지 상세 정보: type={}, originalTopic={}, key={}, retryCount={}, " +
                    "failedAt={}, errorClass={}, errorMessage={}", 
                    messageType, dlqMessage.getOriginalTopic(), dlqMessage.getOriginalKey(),
                    dlqMessage.getRetryCount(), dlqMessage.getFailedAt(), 
                    dlqMessage.getErrorClass(), dlqMessage.getErrorMessage());
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
