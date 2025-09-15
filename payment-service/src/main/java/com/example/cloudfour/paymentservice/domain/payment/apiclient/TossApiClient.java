package com.example.cloudfour.paymentservice.domain.payment.apiclient;

import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossApiClient {

    private final TossFeginClient tossFeginClient;


    public TossApproveResponse approvePayment(String paymentKey, String orderId, Integer amount, String idempotencyKey) {
        try {
            log.info("토스 결제 승인 요청: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);
            TossApproveResponse response = tossFeginClient.approvePayment(
                    idempotencyKey,
                    PaymentRequestDTO.TossApproveRequest.builder()
                    .paymentKey(paymentKey)
                    .orderId(orderId)
                    .amount(amount)
                    .build()
            );
            log.info("토스 결제 승인 성공: paymentKey={}", paymentKey);
            return response;
        } catch (Exception e) {
            log.error("토스 결제 승인 실패: paymentKey={}, error={}", paymentKey, e.getMessage());
            throw e;
        }
    }

    public void cancelPayment(String paymentKey, String cancelReason) {
        try {
            log.info("토스 결제 취소 요청: paymentKey={}, reason={}", paymentKey, cancelReason);
            tossFeginClient.cancelPayment(paymentKey,
                    PaymentRequestDTO.PaymentCancelRequestDTO.builder()
                    .cancelReason(cancelReason).build());
            log.info("토스 결제 취소 성공: paymentKey={}", paymentKey);
        } catch (Exception e) {
            log.error("토스 결제 취소 실패: paymentKey={}, error={}", paymentKey, e.getMessage());
            throw e;
        }
    }

    public static class TossApproveResponse {
        public String paymentKey;
        public String orderId;
        public Integer totalAmount;
        public String method;
        public String status;
        public String approvedAt;
    }
}
