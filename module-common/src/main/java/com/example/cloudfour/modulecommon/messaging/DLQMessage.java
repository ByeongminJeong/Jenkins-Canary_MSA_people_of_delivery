package com.example.cloudfour.modulecommon.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class DLQMessage {

    @JsonProperty("originalTopic")
    private final String originalTopic;

    @JsonProperty("originalKey")
    private final String originalKey;

    @JsonProperty("originalMessage")
    private final Envelope<?> originalMessage;

    @JsonProperty("errorMessage")
    private final String errorMessage;

    @JsonProperty("errorClass")
    private final String errorClass;

    @JsonProperty("retryCount")
    private final int retryCount;

    @JsonProperty("failedAt")
    private final LocalDateTime failedAt;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;
}
