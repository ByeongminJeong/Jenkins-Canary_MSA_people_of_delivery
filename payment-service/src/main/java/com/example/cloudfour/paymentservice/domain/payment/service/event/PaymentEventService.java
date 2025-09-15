package com.example.cloudfour.paymentservice.domain.payment.service.event;

import com.example.cloudfour.paymentservice.domain.payment.converter.PaymentEventConverter;
import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentException;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentErrorCode;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentEvents;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventService {
    
    private final OutboxService outboxService;
    
    @Value("${kafka.topics.paymentEvents:payment.events.v1}")
    private String paymentEventsTopic;

    @Transactional
    public void publishPaymentCreated(Payment payment) {
        try {
            PaymentEvents.PaymentCreated event = PaymentEventConverter.createPaymentCreatedEvent(
                payment.getOrderId(),
                payment.getUserId(),
                payment.getStoreId(),
                payment.getAmount(),
                payment.getPaymentMethod()
            );
            
            outboxService.saveEvent(
                payment.getOrderId().toString(),
                "Payment",
                "PaymentCreated",
                event,
                paymentEventsTopic,
                payment.getOrderId().toString()
            );
            
            log.info("PaymentCreated 이벤트 발행 완료: orderId={}", payment.getOrderId());
            
        } catch (Exception e) {
            log.error("PaymentCreated 이벤트 발행 실패: orderId={}, error={}", 
                    payment.getOrderId(), e.getMessage(), e);
            throw new PaymentException(PaymentErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Transactional
    public void publishPaymentCancelled(Payment payment, String reason) {
        try {
            PaymentEvents.PaymentCanceled event = PaymentEventConverter.createPaymentCanceledEvent(
                payment.getOrderId(),
                payment.getUserId(),
                null,
                reason
            );
            
            outboxService.saveEvent(
                payment.getOrderId().toString(),
                "Payment",
                "PaymentCancelled",
                event,
                paymentEventsTopic,
                payment.getOrderId().toString()
            );
            
            log.info("PaymentCancelled 이벤트 발행 완료: orderId={}, reason={}", 
                    payment.getOrderId(), reason);
            
        } catch (Exception e) {
            log.error("PaymentCancelled 이벤트 발행 실패: orderId={}, error={}", 
                    payment.getOrderId(), e.getMessage(), e);
            throw new PaymentException(PaymentErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
