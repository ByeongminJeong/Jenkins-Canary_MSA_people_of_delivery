package com.example.cloudfour.paymentservice.domain.payment.dto;

import com.example.cloudfour.paymentservice.domain.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PaymentResponseDTO {


    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentConfirmResponseDTO {
        private String paymentKey;
        private String orderId;
        private Integer amount;
        private String paymentMethod;
        private PaymentStatus paymentStatus;
        private LocalDateTime approvedAt;
        private String approvedAtStr;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentCancelResponseDTO {
        private UUID paymentId;
        private PaymentStatus paymentStatus;
        private String cancelReason;
        private LocalDateTime canceledAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDetailResponseDTO {
        private UUID paymentId;
        private String paymentKey;
        private String orderId;
        private Integer amount;
        private String paymentMethod;
        private PaymentStatus paymentStatus;
        private String failedReason;
        private LocalDateTime approvedAt;
        private LocalDateTime canceledAt;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentUserListResponseDTO {
        private List<PaymentDetailResponseDTO> paymentList;
        private Integer totalCount;
    }
}

