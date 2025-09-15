package com.example.cloudfour.paymentservice.domain.payment.apiclient;

import com.example.cloudfour.paymentservice.config.TossFeignConfig;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "tossPayments",
        url = "${toss.base-url:https://api.tosspayments.com}",
        configuration = TossFeignConfig.class  // Passport 인터셉터 적용
)
@CircuitBreaker(name="toss-circuit")
public interface TossFeginClient {

    @PostMapping("/v1/payments/confirm")
    TossApiClient.TossApproveResponse approvePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequestDTO.TossApproveRequest body
    );

    @PostMapping("/v1/payments/{paymentKey}/cancel")
    void cancelPayment(
            @PathVariable("paymentKey") String paymentKey,
            @RequestBody PaymentRequestDTO.PaymentCancelRequestDTO body
    );
}
