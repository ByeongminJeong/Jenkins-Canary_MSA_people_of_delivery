package com.example.cloudfour.paymentservice.domain.payment.service;

import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MessageConsumer;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentCommands;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.modulecommon.messaging.SagaAwareDLQHandler;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import com.example.cloudfour.modulecommon.schedule.ScheduledTaskService;
import com.example.cloudfour.paymentservice.domain.payment.converter.PaymentEventConverter;
import com.example.cloudfour.paymentservice.domain.payment.dto.PaymentRequestDTO;
import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentException;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentErrorCode;
import com.example.cloudfour.paymentservice.domain.payment.repository.PaymentRepository;
import com.example.cloudfour.paymentservice.domain.payment.service.command.PaymentCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandHandler {
    
    private final OutboxService outboxService;
    private final MessageConsumer messageConsumer;
    private final PaymentRepository paymentRepository;
    private final PaymentCommandService paymentCommandService;
    private final ObjectMapper objectMapper;
    private final SagaAwareDLQHandler sagaAwareDLQHandler;
    private final ScheduledTaskService scheduledTaskService;
    
    @Value("${kafka.topics.paymentEvents:payment.events.v1}")
    private String paymentEventsTopic;

    @KafkaListener(topics = "${kafka.topics.paymentCommands:payment.commands.v1}", 
                   groupId = "payment-command-handler")
    @Transactional
    public void handlePaymentCommand(
            @Payload Envelope<?> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();
            
            if (payload instanceof PaymentCommands.CreatePayment) {
                handleCreatePayment((PaymentCommands.CreatePayment) payload, acknowledgment);
            } else if (payload instanceof PaymentCommands.CancelPayment) {
                handleCancelPayment((PaymentCommands.CancelPayment) payload, acknowledgment);
            } else if (payload instanceof LinkedHashMap) {
                PaymentCommands.CreatePayment command = convertLinkedHashMapToCommand((LinkedHashMap<?, ?>) payload);
                handleCreatePayment(command, acknowledgment);
            } else {
                log.warn("알 수 없는 결제 커맨드 타입: {}", payload.getClass().getSimpleName());
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("결제 커맨드 처리 실패: error={}", e.getMessage(), e);

            sagaAwareDLQHandler.handleSagaFailure(topic, key, (Envelope<Object>) envelope, e, acknowledgment);
        }
    }

    private void handleCreatePayment(PaymentCommands.CreatePayment command, Acknowledgment acknowledgment) {
        UUID orderId = command.getOrderId();
        UUID userId = command.getUserId();
        UUID storeId = command.getStoreId();
        Integer amount = command.getAmount();
        
        log.info("결제 정보 저장 커맨드 처리 시작: orderId={}, userId={}, storeId={}, amount={}", 
                orderId, userId, storeId, amount);
        
        try {
            Payment payment = paymentCommandService.createPayment(
                orderId, userId, storeId, amount, command.getPaymentMethod()
            );
            
            log.info("결제 정보 저장 완료: orderId={}, paymentId={}, amount={}", 
                    orderId, payment.getId(), amount);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("결제 정보 저장 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private void handleCancelPayment(PaymentCommands.CancelPayment command, Acknowledgment acknowledgment) {
        UUID orderId = command.getOrderId();
        UUID userId = command.getUserId();
        String paymentKey = command.getPaymentKey();
        String reason = command.getReason();
        
        log.info("결제 취소 커맨드 처리 시작: orderId={}, userId={}, paymentKey={}, reason={}", 
                orderId, userId, paymentKey, reason);
        
        try {
            Payment existingPayment = paymentRepository.findByPaymentKeyAndOrderId(paymentKey, orderId)
                    .orElseThrow(() -> new RuntimeException("결제 정보를 찾을 수 없습니다: paymentKey=" + paymentKey));

            PaymentRequestDTO.PaymentCancelRequestDTO cancelRequest = PaymentRequestDTO.PaymentCancelRequestDTO.builder()
                    .cancelReason(reason)
                    .build();
            
            var cancelResult = paymentCommandService.cancelPayment(cancelRequest, orderId, userId);

            PaymentEvents.PaymentCanceled event = PaymentEventConverter.createPaymentCanceledEvent(
                    orderId, userId, paymentKey, reason);
            
            outboxService.saveEvent(
                    orderId.toString(),
                    "Payment",
                    "PaymentCanceled",
                    event,
                    paymentEventsTopic,
                    orderId.toString()
            );
            
            log.info("결제 취소 처리 완료: orderId={}, paymentKey={}", orderId, paymentKey);
            
        } catch (Exception e) {
            log.error("결제 취소 처리 실패: orderId={}, paymentKey={}", orderId, paymentKey, e);

            PaymentEvents.PaymentCanceled event = PaymentEventConverter.createPaymentCanceledEvent(
                    orderId, userId, paymentKey, "결제 취소 처리 중 오류: " + e.getMessage());
            
            outboxService.saveEvent(
                    orderId.toString(),
                    "Payment",
                    "PaymentCanceled",
                    event,
                    paymentEventsTopic,
                    orderId.toString()
            );
        }
        
        acknowledgment.acknowledge();
    }
    
    private PaymentCommands.CreatePayment convertLinkedHashMapToCommand(LinkedHashMap<?, ?> payload) {
        try {
            return objectMapper.convertValue(payload, PaymentCommands.CreatePayment.class);
        } catch (Exception e) {
            log.error("LinkedHashMap을 CreatePayment로 변환 실패: error={}", e.getMessage(), e);
            throw new PaymentException(PaymentErrorCode.INVALID_INPUT);
        }
    }
}
