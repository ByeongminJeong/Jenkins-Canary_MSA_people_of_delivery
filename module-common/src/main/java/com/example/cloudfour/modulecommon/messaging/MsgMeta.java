package com.example.cloudfour.modulecommon.messaging;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
@Jacksonized
public class MsgMeta {
    String msgId;
    String sagaId;
    String type;
    String version;
    String source;
    String causationId;
    String correlationId;
    Instant timestamp;
    Integer attempt;
}