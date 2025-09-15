package com.example.cloudfour.paymentservice.domain.payment.service;

import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import com.example.cloudfour.modulecommon.schedule.ScheduledTaskService;
import com.example.cloudfour.paymentservice.domain.payment.apiclient.OrderClient;
import com.example.cloudfour.paymentservice.domain.payment.converter.PaymentEventConverter;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentResponseDTO;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentException;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentErrorCode;
import com.example.cloudfour.paymentservice.domain.payment.service.command.PaymentCommandService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessService {
    
    private final PaymentCommandService paymentCommandService;
    private final PaymentCacheService paymentCacheService;
    private final OrderClient orderClient;
    private final OutboxService outboxService;
    private final ScheduledTaskService scheduledTaskService;
    
    @Value("${kafka.topics.paymentEvents:payment.events.v1}")
    private String paymentEventsTopic;
    
    public PaymentSuccessResult processSuccess(String paymentKey, String orderId, Long amount) {
        log.info("결제 성공 처리: orderId={}, paymentKey=***{}", orderId,
                 paymentKey.substring(Math.max(0, paymentKey.length()-4)));

        try {
            var order = orderClient.getOrderById(orderId, null);
            if (order == null) {
                throw new PaymentException(PaymentErrorCode.ORDER_NOT_FOUND);
            }

            long serverAmount = order.getTotalPrice();
            if (serverAmount != amount) {
                log.error("금액 불일치: orderId={}, serverAmount={}, clientAmount={}", orderId, serverAmount, amount);
                throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
            }

            paymentCacheService.savePaymentMapping(orderId, paymentKey);

            String frontendUrl = "http://localhost:3000/payment/success?orderId=" + orderId;

            return PaymentSuccessResult.builder()
                    .success(true)
                    .frontendUrl(frontendUrl)
                    .build();

        } catch (PaymentException ex) {
            log.error("결제 검증 실패: orderId={}, errorCode={}, message={}", orderId, ex.getCode().getCode(), ex.getMessage());

            try {
                var order = orderClient.getOrderById(orderId, null);
                UUID userId = order != null ? order.getUserId() : null;
                String reason = String.format("결제 검증 실패: %s - %s", ex.getCode().getCode(), ex.getMessage());
                
                PaymentEvents.PaymentFailed event = PaymentEventConverter.createPaymentFailedEvent(
                    UUID.fromString(orderId),
                    userId,
                    reason
                );
                
                outboxService.saveEvent(
                    orderId,
                    "Payment",
                    "PaymentFailed",
                    event,
                    paymentEventsTopic,
                    orderId
                );
                
                log.info("결제 검증 실패 이벤트 발행 완료: orderId={}, reason={}", orderId, reason);
                
            } catch (Exception e) {
                log.error("결제 검증 실패 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            }

            String code = ex.getCode().getCode();
            String frontendUrl = "http://localhost:3000/payment/fail?orderId=" + orderId + "&code=" + code;

            return PaymentSuccessResult.builder()
                    .success(false)
                    .frontendUrl(frontendUrl)
                    .build();

        } catch (Exception ex) {
            log.error("결제 검증 실패: orderId={}, err={}", orderId, ex.toString(), ex);

            try {
                var order = orderClient.getOrderById(orderId, null);
                UUID userId = order != null ? order.getUserId() : null;
                String reason = String.format("결제 검증 실패: VALIDATION_FAILED - %s", ex.getMessage());
                
                PaymentEvents.PaymentFailed event = PaymentEventConverter.createPaymentFailedEvent(
                    UUID.fromString(orderId),
                    userId,
                    reason
                );
                
                outboxService.saveEvent(
                    orderId,
                    "Payment",
                    "PaymentFailed",
                    event,
                    paymentEventsTopic,
                    orderId
                );
                
                log.info("결제 검증 실패 이벤트 발행 완료: orderId={}, reason={}", orderId, reason);
                
            } catch (Exception e) {
                log.error("결제 검증 실패 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            }

            String code = "VALIDATION_FAILED";
            String frontendUrl = "http://localhost:3000/payment/fail?orderId=" + orderId + "&code=" + code;

            return PaymentSuccessResult.builder()
                    .success(false)
                    .frontendUrl(frontendUrl)
                    .build();
        }
    }
    
    public PaymentConfirmResult processConfirm(String orderId, Integer amount, UUID userId) {
        log.info("결제 승인 처리: orderId={}, userId={}", orderId, userId);

        try {
            String paymentKey = paymentCacheService.getPaymentKeyByOrderId(orderId);
            if (paymentKey == null) {
                throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND);
            }

            var order = orderClient.getOrderById(orderId, userId);
            if (order == null) {
                throw new PaymentException(PaymentErrorCode.ORDER_NOT_FOUND);
            }

            long serverAmount = order.getTotalPrice();
            if (serverAmount != amount) {
                log.error("금액 불일치: orderId={}, serverAmount={}, clientAmount={}",
                         orderId, serverAmount, amount);
                throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
            }

            var confirmRequest = PaymentRequestDTO.PaymentConfirmRequestDTO.builder()
                    .paymentKey(paymentKey)
                    .orderId(orderId)
                    .amount((int) serverAmount)
                    .build();

            PaymentResponseDTO.PaymentConfirmResponseDTO response = paymentCommandService.confirmPayment(confirmRequest, userId);

            paymentCacheService.deletePaymentMapping(orderId, paymentKey);

            try {
                UUID storeId = order.getStoreId();
                
                PaymentEvents.PaymentAuthorized event = PaymentEventConverter.createPaymentAuthorizedEvent(
                    UUID.fromString(orderId),
                    userId,
                    storeId,
                    paymentKey,
                    BigDecimal.valueOf(serverAmount),
                    response.getPaymentMethod()
                );
                
                outboxService.saveEvent(
                    orderId,
                    "Payment",
                    "PaymentAuthorized",
                    event,
                    paymentEventsTopic,
                    orderId
                );
                
                log.info("결제 승인 이벤트 발행 완료: orderId={}", orderId);

                cancelOrderTimeout(orderId);
                
            } catch (Exception e) {
                log.error("결제 승인 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            }

            log.info("결제 승인 완료: orderId={}, paymentKey=***{}", orderId,
                    paymentKey.substring(Math.max(0, paymentKey.length()-4)));

            return PaymentConfirmResult.builder()
                    .success(true)
                    .response(response)
                    .build();

        } catch (PaymentException ex) {
            log.error("결제 승인 실패: orderId={}, errorCode={}, message={}",
                     orderId, ex.getCode().getCode(), ex.getMessage());
            
            try {
                var order = orderClient.getOrderById(orderId, userId);
                UUID orderUserId = order != null ? order.getUserId() : userId;
                String reason = String.format("결제 승인 실패: %s - %s", ex.getCode().getCode(), ex.getMessage());
                
                PaymentEvents.PaymentFailed event = PaymentEventConverter.createPaymentFailedEvent(
                    UUID.fromString(orderId),
                    orderUserId,
                    reason
                );
                
                outboxService.saveEvent(
                    orderId,
                    "Payment",
                    "PaymentFailed",
                    event,
                    paymentEventsTopic,
                    orderId
                );
                
                log.info("결제 승인 실패 이벤트 발행 완료: orderId={}, reason={}", orderId, reason);
                
            } catch (Exception e) {
                log.error("결제 승인 실패 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            }
            
            return PaymentConfirmResult.builder()
                    .success(false)
                    .exception(ex)
                    .build();
        } catch (Exception ex) {
            log.error("결제 승인 실패: orderId={}, error={}", orderId, ex.getMessage(), ex);
            
            try {
                var order = orderClient.getOrderById(orderId, userId);
                UUID orderUserId = order != null ? order.getUserId() : userId;
                String reason = String.format("결제 승인 실패: PAYMENT_APPROVAL_FAILED - %s", ex.getMessage());
                
                PaymentEvents.PaymentFailed event = PaymentEventConverter.createPaymentFailedEvent(
                    UUID.fromString(orderId),
                    orderUserId,
                    reason
                );
                
                outboxService.saveEvent(
                    orderId,
                    "Payment",
                    "PaymentFailed",
                    event,
                    paymentEventsTopic,
                    orderId
                );
                
                log.info("결제 승인 실패 이벤트 발행 완료: orderId={}, reason={}", orderId, reason);
                
            } catch (Exception e) {
                log.error("결제 승인 실패 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            }
            
            return PaymentConfirmResult.builder()
                    .success(false)
                    .exception(new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_FAILED))
                    .build();
        }
    }
    
    public void processFailure(String orderId, String code, String message) {
        log.warn("결제 실패 처리: orderId={}, code={}, msg(len={})", 
                 orderId, code, message != null ? message.length() : 0);

        try {
            var order = orderClient.getOrderById(orderId, null);
            if (order == null) {
                log.error("주문을 찾을 수 없습니다: orderId={}", orderId);
            }

            try {
                UUID userId = order != null ? order.getUserId() : null;
                String reason = String.format("결제 실패: code=%s, message=%s", code, message);
                
                PaymentEvents.PaymentFailed event = PaymentEventConverter.createPaymentFailedEvent(
                    UUID.fromString(orderId),
                    userId,
                    reason
                );
                
                outboxService.saveEvent(
                    orderId,
                    "Payment",
                    "PaymentFailed",
                    event,
                    paymentEventsTopic,
                    orderId
                );
                
                log.info("결제 실패 이벤트 발행 완료: orderId={}, reason={}", orderId, reason);
                
            } catch (Exception e) {
                log.error("결제 실패 이벤트 발행 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("결제 실패 처리 중 오류: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }
    
    @Builder
    @Getter
    public static class PaymentSuccessResult {
        private final boolean success;
        private final String frontendUrl;
    }
    
    @Builder
    @Getter
    public static class PaymentConfirmResult {
        private final boolean success;
        private final PaymentResponseDTO.PaymentConfirmResponseDTO response;
        private final PaymentException exception;
    }

    private void cancelOrderTimeout(String orderId) {
        try {
            scheduledTaskService.cancelOrderTimeout(orderId);
            log.info("주문 타임아웃 스케줄 취소 완료: orderId={}", orderId);
        } catch (Exception e) {
            log.error("주문 타임아웃 스케줄 취소 실패: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }
}
