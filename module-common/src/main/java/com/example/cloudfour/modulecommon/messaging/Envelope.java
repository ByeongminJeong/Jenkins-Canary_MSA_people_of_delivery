package com.example.cloudfour.modulecommon.messaging;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Envelope<T> {
    MsgMeta meta;
    T payload;
}