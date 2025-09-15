package com.example.cloudfour.paymentservice.domain.payment.converter;

import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentEventConverter {

    public static PaymentEvents.PaymentCreated createPaymentCreatedEvent(
            UUID orderId, UUID userId, UUID storeId, Integer amount, String paymentMethod) {
        return PaymentEvents.PaymentCreated.builder()
                .orderId(orderId)
                .userId(userId)
                .storeId(storeId)
                .amount(BigDecimal.valueOf(amount))
                .paymentMethod(paymentMethod)
                .paymentStatus("PENDING")
                .createdAt(Instant.now())
                .build();
    }

    public static PaymentEvents.PaymentAuthorized createPaymentAuthorizedEvent(
            UUID orderId, UUID userId, UUID storeId, String paymentKey, 
            BigDecimal amount, String paymentMethod) {
        return PaymentEvents.PaymentAuthorized.builder()
                .orderId(orderId)
                .userId(userId)
                .storeId(storeId)
                .paymentKey(paymentKey)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .authorizedAt(Instant.now())
                .build();
    }

    public static PaymentEvents.PaymentFailed createPaymentFailedEvent(
            UUID orderId, UUID userId, String reason) {
        return PaymentEvents.PaymentFailed.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .failedAt(Instant.now())
                .build();
    }

    public static PaymentEvents.PaymentCanceled createPaymentCanceledEvent(
            UUID orderId, UUID userId, String paymentKey, String reason) {
        return PaymentEvents.PaymentCanceled.builder()
                .orderId(orderId)
                .userId(userId)
                .paymentKey(paymentKey)
                .reason(reason)
                .canceledAt(Instant.now())
                .build();
    }

    public static PaymentRequestDTO.PaymentConfirmRequestDTO createPaymentConfirmRequest(
            String paymentKey, String orderId, Integer amount) {
        return PaymentRequestDTO.PaymentConfirmRequestDTO.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .amount(amount)
                .build();
    }

    public static PaymentRequestDTO.PaymentCancelRequestDTO createPaymentCancelRequest(String reason) {
        return PaymentRequestDTO.PaymentCancelRequestDTO.builder()
                .cancelReason(reason)
                .build();
    }
}
