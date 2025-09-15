package com.example.cloudfour.modulecommon.outbox.publisher;

import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MsgMeta;
import com.example.cloudfour.modulecommon.converter.MessageConverter;
import com.example.cloudfour.modulecommon.outbox.event.OutboxEventReadyToPublish;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = false)
public class TransactionalOutboxPublisher {
    
    private final OutboxService outboxService;
    private final KafkaTemplate<String, Envelope<?>> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEventReadyToPublish(OutboxEventReadyToPublish event) {
        log.debug("AFTER_COMMIT: 카프카 발행 시작 - eventId={}", event.getEventId());
        
        try {
            publishEvent(event);
        } catch (Exception e) {
            log.error("AFTER_COMMIT: 카프카 발행 실패 - eventId={}, error={}", 
                    event.getEventId(), e.getMessage(), e);
            outboxService.markEventAsFailed(event.getEventId(), e.getMessage());
        }
    }

    private void publishEvent(OutboxEventReadyToPublish event) {
        try {
            Object eventData = objectMapper.readValue(event.getEventData(), Object.class);

            MsgMeta meta = MessageConverter.toMsgMeta(event);
            Envelope<Object> envelope = MessageConverter.toEnvelope(meta, eventData);

            CompletableFuture<SendResult<String, Envelope<?>>> future = 
                    kafkaTemplate.send(event.getTopic(), event.getKey(), envelope);
            
            future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    outboxService.markEventAsPublished(event.getEventId());
                    log.info("AFTER_COMMIT: 카프카 발행 성공 - eventId={}, topic={}, partition={}, offset={}", 
                            event.getEventId(), event.getTopic(), 
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    outboxService.markEventAsFailed(event.getEventId(), throwable.getMessage());
                    log.error("AFTER_COMMIT: 카프카 발행 실패 - eventId={}, error={}", 
                            event.getEventId(), throwable.getMessage(), throwable);
                }
            });
            
        } catch (Exception e) {
            outboxService.markEventAsFailed(event.getEventId(), e.getMessage());
            throw new RuntimeException("카프카 발행 실패", e);
        }
    }
}
