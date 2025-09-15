package com.example.cloudfour.modulecommon.converter;

import com.example.cloudfour.modulecommon.messaging.payment.PaymentCommands;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentCommandConverter {

    public static PaymentCommands.CreatePayment toCreatePaymentCommand(String orderId, String userId, String storeId, BigDecimal amount) {
        return PaymentCommands.CreatePayment.builder()
                .orderId(UUID.fromString(orderId))
                .userId(UUID.fromString(userId))
                .storeId(UUID.fromString(storeId))
                .amount(amount.intValue())
                .paymentMethod("CARD")
                .requestedAt(Instant.now())
                .build();
    }


    public static PaymentCommands.CancelPayment toCancelPaymentCommand(String orderId, String userId, String paymentKey, String reason) {
        return PaymentCommands.CancelPayment.builder()
                .orderId(UUID.fromString(orderId))
                .userId(UUID.fromString(userId))
                .paymentKey(paymentKey)
                .reason(reason)
                .requestedAt(Instant.now())
                .build();
    }
}
