package com.example.cloudfour.paymentservice.domain.payment.converter;

import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentResponseDTO;
import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;
import com.example.cloudfour.paymentservice.domain.payment.entity.PaymentHistory;
import com.example.cloudfour.paymentservice.domain.payment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class PaymentConverter {

    public PaymentResponseDTO.PaymentDetailResponseDTO toDetailResponse(Payment payment) {
        return PaymentResponseDTO.PaymentDetailResponseDTO.builder()
                .paymentId(payment.getId())
                .paymentKey(payment.getPaymentKey())
                .orderId(payment.getOrderId().toString())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .failedReason(payment.getFailedReason())
                .approvedAt(payment.getApprovedAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    public PaymentResponseDTO.PaymentConfirmResponseDTO toConfirmResponse(Payment payment) {
        return PaymentResponseDTO.PaymentConfirmResponseDTO.builder()
                .paymentKey(payment.getPaymentKey())
                .orderId(payment.getOrderId().toString())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .approvedAt(payment.getApprovedAt())
                .approvedAtStr(payment.getApprovedAt() != null ? 
                    payment.getApprovedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
                .build();
    }

    public PaymentResponseDTO.PaymentCancelResponseDTO toCancelResponse(Payment payment, PaymentHistory history) {
        var responseStatus = history.getCurrentStatus() != null ? 
            history.getCurrentStatus() : payment.getPaymentStatus();
            
        return PaymentResponseDTO.PaymentCancelResponseDTO.builder()
                .paymentId(payment.getId())
                .paymentStatus(responseStatus)
                .cancelReason(history.getChangeReason())
                .canceledAt(payment.getCanceledAt())
                .build();
    }

    public PaymentHistory createPaymentApprovedHistory(Payment payment, String rawResponse) {
        return PaymentHistory.builder()
                .payment(payment)
                .previousStatus(null)
                .currentStatus(PaymentStatus.APPROVED)
                .changeReason("토스페이먼츠 결제 승인")
                .rawResponse(rawResponse)
                .build();
    }

    public PaymentHistory createPaymentFailedHistory(Payment payment, String errorMessage) {
        return PaymentHistory.builder()
                .payment(payment)
                .previousStatus(null)
                .currentStatus(PaymentStatus.FAILED)
                .changeReason("토스페이먼츠 결제 승인 실패: " + errorMessage)
                .rawResponse("{\"error\":\"" + errorMessage + "\"}")
                .build();
    }

    public PaymentHistory createPaymentCanceledHistory(Payment payment, String cancelReason, String rawResponse) {
        return PaymentHistory.builder()
                .payment(payment)
                .previousStatus(PaymentStatus.APPROVED)
                .currentStatus(PaymentStatus.CANCELED)
                .changeReason("토스페이먼츠 결제 취소: " + cancelReason)
                .rawResponse(rawResponse)
                .build();
    }

    public PaymentHistory createPaymentCancelFailedHistory(Payment payment, String errorMessage) {
        return PaymentHistory.builder()
                .payment(payment)
                .previousStatus(payment.getPaymentStatus())
                .currentStatus(payment.getPaymentStatus())
                .changeReason("토스페이먼츠 결제 취소 실패: " + errorMessage)
                .rawResponse("{\"error\":\"" + errorMessage + "\"}")
                .build();
    }
}

