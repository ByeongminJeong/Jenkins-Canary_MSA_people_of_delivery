package com.example.cloudfour.paymentservice.domain.payment.service.command;

import com.example.cloudfour.paymentservice.domain.payment.converter.PaymentConverter;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentResponseDTO;
import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;
import com.example.cloudfour.paymentservice.domain.payment.entity.PaymentHistory;
import com.example.cloudfour.paymentservice.domain.payment.enums.PaymentStatus;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentErrorCode;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentException;
import com.example.cloudfour.paymentservice.domain.payment.repository.PaymentHistoryRepository;
import com.example.cloudfour.paymentservice.domain.payment.repository.PaymentRepository;
import com.example.cloudfour.paymentservice.domain.payment.service.IdempotencyService;
import com.example.cloudfour.paymentservice.domain.payment.service.event.PaymentEventService;
import com.example.cloudfour.paymentservice.domain.payment.apiclient.TossApiClient;
import com.example.cloudfour.paymentservice.domain.payment.apiclient.UserClient;
import com.example.cloudfour.paymentservice.domain.payment.apiclient.OrderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandServiceImpl implements PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentConverter paymentConverter;
    private final TossApiClient tossApiClient;
    private final UserClient userClient;
    private final OrderClient orderClient;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final PaymentEventService paymentEventService;


    @Override
    public PaymentResponseDTO.PaymentConfirmResponseDTO confirmPayment(PaymentRequestDTO.PaymentConfirmRequestDTO request, UUID userId) {
        log.info("결제 승인 시작: paymentKey={}, orderId={}, userId={}", 
                request.getPaymentKey(), request.getOrderId(), userId);

        if (!userClient.existsUser(userId)) {
            log.error("존재하지 않는 사용자: userId={}", userId);
            throw new PaymentException(PaymentErrorCode.USER_NOT_FOUND);
        }

        Optional<Payment> existingPayment = idempotencyService.checkPaymentApprovalIdempotency(request.getPaymentKey());
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            log.info("이미 처리된 결제 (멱등성): paymentKey={}, status={}", 
                    request.getPaymentKey(), payment.getPaymentStatus());
            return paymentConverter.toConfirmResponse(payment);
        }

        Payment pendingPayment = paymentRepository.findByOrderIdAndUserId(
                UUID.fromString(request.getOrderId()), userId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (pendingPayment.getPaymentStatus() == PaymentStatus.APPROVED) {
            log.info("이미 승인된 결제 (멱등성): orderId={}, paymentKey={}", request.getOrderId(), request.getPaymentKey());
            return paymentConverter.toConfirmResponse(pendingPayment);
        }

        if (!pendingPayment.getAmount().equals(request.getAmount())) {
            log.error("결제 금액 불일치: 저장된 금액={}, 요청 금액={}", 
                    pendingPayment.getAmount(), request.getAmount());
            throw new PaymentException(PaymentErrorCode.INVALID_INPUT);
        }

        String idempotencyKey = idempotencyService.generateIdempotencyKey();

        try {
            TossApiClient.TossApproveResponse tossResponse = tossApiClient.approvePayment(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount(),
                idempotencyKey
            );

            pendingPayment.updatePaymentInfo(
                    tossResponse.paymentKey,
                    tossResponse.totalAmount,
                    tossResponse.method,
                    PaymentStatus.APPROVED,
                    LocalDateTime.now(),
                    objectMapper.writeValueAsString(tossResponse)
            );

            idempotencyService.setPaymentApprovalIdempotency(pendingPayment);
            Payment payment = paymentRepository.save(pendingPayment);

            PaymentHistory history = paymentConverter.createPaymentApprovedHistory(
                    payment, objectMapper.writeValueAsString(tossResponse));

            idempotencyService.setPaymentCancelIdempotency(history);
            paymentHistoryRepository.save(history);

            log.info("결제 승인 완료: paymentId={}, paymentKey={}", payment.getId(), payment.getPaymentKey());
            return paymentConverter.toConfirmResponse(payment);

        } catch (Exception e) {
            log.error("결제 승인 실패: paymentKey={}, error={}", request.getPaymentKey(), e.getMessage());

            Payment failedPayment = Payment.builder()
                    .paymentKey(request.getPaymentKey())
                    .orderId(UUID.fromString(request.getOrderId()))
                    .userId(userId)
                    .amount(request.getAmount())
                    .paymentMethod("UNKNOWN")
                    .paymentStatus(PaymentStatus.FAILED)
                    .failedReason(e.getMessage())
                    .rawResponse("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();

            failedPayment = paymentRepository.save(failedPayment);

            PaymentHistory failedHistory = paymentConverter.createPaymentFailedHistory(
                    failedPayment, e.getMessage());

            paymentHistoryRepository.save(failedHistory);

            throw new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_FAILED);
        }
    }

    private UUID getStoreIdFromOrder(String orderId, UUID userId) {
        try {
            return orderClient.getOrderById(orderId, userId).getStoreId();
        } catch (Exception e) {
            log.error("주문에서 가게 ID 조회 실패: orderId={}, userId={}, error={}", orderId, userId, e.getMessage());
            throw new PaymentException(PaymentErrorCode.STORE_NOT_FOUND);
        }
    }

    @Override
    public PaymentResponseDTO.PaymentCancelResponseDTO cancelPayment(PaymentRequestDTO.PaymentCancelRequestDTO request, UUID orderId, UUID userId) {
        log.info("결제 취소 시작: orderId={}, userId={}, reason={}", orderId, userId, request.getCancelReason());

        if (!userClient.existsUser(userId)) {
            log.error("존재하지 않는 사용자: userId={}", userId);
            throw new PaymentException(PaymentErrorCode.USER_NOT_FOUND);
        }

        Payment payment = paymentRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.canCancel()) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        try {
            tossApiClient.cancelPayment(
                payment.getPaymentKey(),
                request.getCancelReason()
            );

            payment.cancel(request.getCancelReason(), LocalDateTime.now(), "{\"cancelReason\":\"" + request.getCancelReason() + "\"}");
            payment = paymentRepository.save(payment);

            PaymentHistory history = paymentConverter.createPaymentCanceledHistory(
                    payment, request.getCancelReason(), "{\"cancelReason\":\"" + request.getCancelReason() + "\"}");

            idempotencyService.setPaymentCancelIdempotency(history);
            paymentHistoryRepository.save(history);

            log.info("결제 취소 완료: paymentId={}, paymentKey={}", payment.getId(), payment.getPaymentKey());
            return paymentConverter.toCancelResponse(payment, history);

        } catch (Exception e) {
            log.error("결제 취소 실패: paymentKey={}, error={}", payment.getPaymentKey(), e.getMessage());

            PaymentHistory failedHistory = paymentConverter.createPaymentCancelFailedHistory(
                    payment, e.getMessage());

            paymentHistoryRepository.save(failedHistory);

            throw new PaymentException(PaymentErrorCode.PAYMENT_CANCEL_FAILED);
        }
    }

    public Payment createPayment(UUID orderId, UUID userId, UUID storeId, Integer amount, String paymentMethod) {
        log.info("결제 정보 생성 시작: orderId={}, userId={}, storeId={}, amount={}", 
                orderId, userId, storeId, amount);
        
        if (paymentRepository.existsByOrderIdAndUserId(orderId, userId)) {
            log.info("이미 결제 정보가 존재함: orderId={}, userId={}", orderId, userId);
            return paymentRepository.findByOrderIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다"));
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .storeId(storeId)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);

        paymentEventService.publishPaymentCreated(savedPayment);
        
        log.info("결제 정보 생성 완료: orderId={}, paymentId={}, amount={}", 
                orderId, savedPayment.getId(), amount);
        
        return savedPayment;
    }

    @Override
    public void updateStatusFromWebhook(String payload) {
        log.info("웹훅으로부터 결제 상태 업데이트: payload={}", payload);
        try {
            // TODO: 웹훅 처리 로직 구현
            
        } catch (Exception e) {
            log.error("웹훅 처리 실패: payload={}, error={}", payload, e.getMessage(), e);
            throw new PaymentException(PaymentErrorCode.WEBHOOK_PROCESSING_FAILED);
        }
    }
}