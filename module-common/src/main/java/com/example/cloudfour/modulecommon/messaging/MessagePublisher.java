package com.example.cloudfour.modulecommon.messaging;

import com.example.cloudfour.modulecommon.converter.MessageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public <T> CompletableFuture<SendResult<String, Object>> publishEvent(
            String topic,
            String key,
            T payload,
            String source,
            String sagaId,
            String causationId) {
        
        MsgMeta meta = MsgMeta.builder()
                .msgId(UUID.randomUUID().toString())
                .sagaId(sagaId)
                .type(payload.getClass().getSimpleName())
                .version("v1")
                .source(source)
                .causationId(causationId)
                .correlationId(sagaId)
                .timestamp(Instant.now())
                .attempt(0)
                .build();
        
        Envelope<T> envelope = MessageConverter.toEnvelope(meta, payload);
        
        log.info("이벤트 발행: topic={}, key={}, type={}, msgId={}", 
                topic, key, meta.getType(), meta.getMsgId());
        
        return kafkaTemplate.send(topic, key, envelope);
    }

    public <T> CompletableFuture<SendResult<String, Object>> publishCommand(
            String topic,
            String key,
            T payload,
            String source,
            String sagaId,
            String causationId) {
        
        MsgMeta meta = MsgMeta.builder()
                .msgId(UUID.randomUUID().toString())
                .sagaId(sagaId)
                .type(payload.getClass().getSimpleName())
                .version("v1")
                .source(source)
                .causationId(causationId)
                .correlationId(sagaId)
                .timestamp(Instant.now())
                .attempt(0)
                .build();
        
        Envelope<T> envelope = MessageConverter.toEnvelope(meta, payload);
        
        log.info("커맨드 발행: topic={}, key={}, type={}, msgId={}", 
                topic, key, meta.getType(), meta.getMsgId());
        
        return kafkaTemplate.send(topic, key, envelope);
    }
}
