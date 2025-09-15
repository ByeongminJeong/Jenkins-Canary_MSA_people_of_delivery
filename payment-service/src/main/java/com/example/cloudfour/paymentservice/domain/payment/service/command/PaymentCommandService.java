package com.example.cloudfour.paymentservice.domain.payment.service.command;

import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentResponseDTO;
import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;

import java.util.UUID;

public interface PaymentCommandService {
    PaymentResponseDTO.PaymentConfirmResponseDTO confirmPayment(PaymentRequestDTO.PaymentConfirmRequestDTO request, UUID userId);
    PaymentResponseDTO.PaymentCancelResponseDTO cancelPayment(PaymentRequestDTO.PaymentCancelRequestDTO request, UUID orderId, UUID userId);
    void updateStatusFromWebhook(String payload);
    Payment createPayment(UUID orderId, UUID userId, UUID storeId, Integer amount, String paymentMethod);
}
