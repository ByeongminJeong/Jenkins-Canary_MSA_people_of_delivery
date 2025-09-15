package com.example.cloudfour.paymentservice.domain.payment.entity;

import com.example.cloudfour.paymentservice.domain.payment.enums.PaymentStatus;
import com.example.cloudfour.modulecommon.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "p_payment")
public class Payment extends BaseEntity {
    
    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Column(length = 500)
    private String failedReason;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private LocalDateTime canceledAt;

    @Column(length = 1000)
    private String rawResponse;

    @Column(length = 100)
    private String idempotencyKey;

    public void approve(LocalDateTime approvedAt, String rawResponse) {
        this.paymentStatus = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt;
        this.rawResponse = maskSensitiveInfo(rawResponse);
    }

    public void approve(LocalDateTime approvedAt) {
        this.paymentStatus = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    public void fail(String reason, String rawResponse) {
        this.paymentStatus = PaymentStatus.FAILED;
        this.failedReason = reason;
        this.rawResponse = maskSensitiveInfo(rawResponse);
    }

    public void fail(String reason, LocalDateTime failedAt) {
        this.paymentStatus = PaymentStatus.FAILED;
        this.failedReason = reason;
    }

    public void cancel(String reason, LocalDateTime canceledAt, String rawResponse) {
        this.paymentStatus = PaymentStatus.CANCELED;
        this.canceledAt = canceledAt;
        this.failedReason = reason;
        this.rawResponse = maskSensitiveInfo(rawResponse);
    }

    public void updateStatus(PaymentStatus newStatus) {
        this.paymentStatus = newStatus;
    }

    public void setFailedReason(String reason) {
        this.failedReason = reason;
    }

    public void updatePaymentInfo(String paymentKey, Integer amount, String paymentMethod, 
                                 PaymentStatus status, LocalDateTime approvedAt, String rawResponse) {
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = status;
        this.approvedAt = approvedAt;
        this.rawResponse = maskSensitiveInfo(rawResponse);
    }



    public boolean canCancel() {
        return PaymentStatus.APPROVED.equals(this.paymentStatus);
    }
    
    public LocalDateTime getApprovedAt() {
        return this.approvedAt;
    }

    private String maskSensitiveInfo(String rawResponse) {
        if (rawResponse == null) return null;
        return rawResponse.replaceAll("(\"cardNumber\":\\s*\")([0-9]{4})([0-9]{4})([0-9]{4})([0-9]{4})(\")", 
                                   "$1$2****$4****$6")
                        .replaceAll("(\"accountNumber\":\\s*\")([0-9]{1,})(\")", 
                                   "$1****$3");
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public static class PaymentBuilder {
        private PaymentBuilder id(UUID id) {
            throw new UnsupportedOperationException("id 수동 생성 불가");
        }
    }
}


