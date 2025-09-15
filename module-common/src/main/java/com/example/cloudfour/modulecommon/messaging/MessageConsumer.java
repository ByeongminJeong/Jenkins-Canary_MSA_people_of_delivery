package com.example.cloudfour.modulecommon.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class MessageConsumer {
    
    private final ObjectMapper objectMapper;

    public void logMessageReceived(
            @Payload Envelope<?> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        
        MsgMeta meta = envelope.getMeta();
        log.info("메시지 수신: topic={}, partition={}, offset={}, key={}, type={}, msgId={}, sagaId={}", 
                topic, partition, offset, key, meta.getType(), meta.getMsgId(), meta.getSagaId());
    }

    public void logMessageProcessingError(
            String topic,
            String key,
            String msgId,
            String sagaId,
            String type,
            Exception error) {
        
        log.error("메시지 처리 실패: topic={}, key={}, type={}, msgId={}, sagaId={}, error={}", 
                topic, key, type, msgId, sagaId, error.getMessage(), error);
    }
}


