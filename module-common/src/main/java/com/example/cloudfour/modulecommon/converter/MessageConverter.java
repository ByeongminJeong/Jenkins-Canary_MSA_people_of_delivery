package com.example.cloudfour.modulecommon.converter;

import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MsgMeta;
import com.example.cloudfour.modulecommon.outbox.entity.OutboxEvent;
import com.example.cloudfour.modulecommon.outbox.event.OutboxEventReadyToPublish;

import java.time.Instant;
import java.util.UUID;

public class MessageConverter {

    public static MsgMeta toMsgMeta(OutboxEvent event) {
        return MsgMeta.builder()
                .msgId(UUID.randomUUID().toString())
                .sagaId(event.getAggregateId())
                .type(event.getEventType())
                .version("v1")
                .source("outbox-publisher")
                .causationId(null)
                .correlationId(event.getAggregateId())
                .timestamp(Instant.now())
                .attempt(event.getRetryCount())
                .build();
    }

    public static <T> Envelope<T> toEnvelope(MsgMeta meta, T payload) {
        return Envelope.<T>builder()
                .meta(meta)
                .payload(payload)
                .build();
    }

    public static <T> Envelope<T> toEnvelope(OutboxEvent event, T payload) {
        MsgMeta meta = toMsgMeta(event);
        return toEnvelope(meta, payload);
    }

    public static MsgMeta toMsgMeta(OutboxEventReadyToPublish event) {
        return MsgMeta.builder()
                .msgId(UUID.randomUUID().toString())
                .sagaId(event.getAggregateId())
                .type(event.getEventType())
                .version("v1")
                .source("outbox-publisher")
                .causationId(null)
                .correlationId(event.getAggregateId())
                .timestamp(Instant.now())
                .attempt(0)
                .build();
    }

    public static <T> Envelope<T> toEnvelope(OutboxEventReadyToPublish event, T payload) {
        MsgMeta meta = toMsgMeta(event);
        return toEnvelope(meta, payload);
    }
}
