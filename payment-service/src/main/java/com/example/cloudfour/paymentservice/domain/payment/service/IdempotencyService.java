package com.example.cloudfour.paymentservice.domain.payment.service;

import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;
import com.example.cloudfour.paymentservice.domain.payment.entity.PaymentHistory;
import com.example.cloudfour.paymentservice.domain.payment.repository.PaymentHistoryRepository;
import com.example.cloudfour.paymentservice.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    private static final long IDEMPOTENCY_EXPIRY_HOURS = 24;

    public String generateIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    @Transactional(readOnly = true)
    public Optional<Payment> checkPaymentApprovalIdempotency(String paymentKey) {
        log.debug("결제 승인 멱등성 체크: paymentKey={}", paymentKey);
        return paymentRepository.findByPaymentKey(paymentKey);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentHistory> checkPaymentCancelIdempotency(String idempotencyKey) {
        log.debug("결제 취소 멱등성 체크: idempotencyKey={}", idempotencyKey);
        return paymentHistoryRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentHistory> checkWebhookIdempotency(String idempotencyKey) {
        log.debug("웹훅 멱등성 체크: idempotencyKey={}", idempotencyKey);
        return paymentHistoryRepository.findByIdempotencyKey(idempotencyKey);
    }

    public String setPaymentApprovalIdempotency(Payment payment) {
        String idempotencyKey = generateIdempotencyKey();
        payment.setIdempotencyKey(idempotencyKey);
        log.debug("결제 승인 멱등키 설정: paymentId={}, idempotencyKey={}", payment.getId(), idempotencyKey);
        return idempotencyKey;
    }

    public String setPaymentCancelIdempotency(PaymentHistory history) {
        String idempotencyKey = generateIdempotencyKey();
        history.setIdempotencyKey(idempotencyKey);
        log.debug("결제 취소 멱등키 설정: historyId={}, idempotencyKey={}", history.getId(), idempotencyKey);
        return idempotencyKey;
    }

    public String setWebhookIdempotency(PaymentHistory history) {
        String idempotencyKey = generateIdempotencyKey();
        history.setIdempotencyKey(idempotencyKey);
        log.debug("웹훅 멱등키 설정: historyId={}, idempotencyKey={}", history.getId(), idempotencyKey);
        return idempotencyKey;
    }

    public boolean isIdempotencyKeyValid(Instant createdAt) {
        if (createdAt == null) {
            return false;
        }
        
        Instant expiryTime = createdAt.plusSeconds(IDEMPOTENCY_EXPIRY_HOURS * 3600);
        boolean isValid = Instant.now().isBefore(expiryTime);
        
        if (!isValid) {
            log.warn("멱등키 만료: createdAt={}, expiryTime={}", createdAt, expiryTime);
        }
        
        return isValid;
    }
}
