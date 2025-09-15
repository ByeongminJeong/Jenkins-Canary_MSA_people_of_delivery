package com.example.cloudfour.modulecommon.outbox.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class OutboxEventReadyToPublish {
    private final UUID eventId;
    private final String aggregateId;
    private final String aggregateType;
    private final String eventType;
    private final String eventData;
    private final String topic;
    private final String key;
}
