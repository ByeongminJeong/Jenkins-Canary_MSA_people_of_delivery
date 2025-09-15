package com.example.cloudfour.cartservice.domain.order.service.event;

import com.example.cloudfour.cartservice.domain.order.converter.OrderEventConverter;
import com.example.cloudfour.cartservice.domain.order.entity.Order;
import com.example.cloudfour.cartservice.domain.order.exception.OrderException;
import com.example.cloudfour.cartservice.domain.order.exception.OrderErrorCode;
import com.example.cloudfour.modulecommon.messaging.order.OrderEvents;
import com.example.cloudfour.modulecommon.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventService {
    
    private final OutboxService outboxService;
    private final OrderEventConverter orderEventConverter;
    
    @Value("${kafka.topics.orderEvents:order.events.v1}")
    private String orderEventsTopic;

    @Transactional
    public void publishOrderCreated(Order order) {
        try {
            OrderEvents.OrderCreated event = orderEventConverter.toOrderCreatedEvent(order);
            
            outboxService.saveEvent(
                    order.getId().toString(),
                    "Order",
                    "OrderCreated",
                    event,
                    orderEventsTopic,
                    order.getId().toString()
            );
            
            log.info("주문 생성 이벤트 저장 완료: orderId={}", order.getId());
            
        } catch (Exception e) {
            log.error("주문 생성 이벤트 저장 실패: orderId={}, error={}", 
                    order.getId(), e.getMessage(), e);
            throw new OrderException(OrderErrorCode.INTERNAL_ERROR);
        }
    }

    @Transactional
    public void publishOrderApproved(Order order) {
        try {
            OrderEvents.OrderApproved event = orderEventConverter.toOrderApprovedEvent(order);
            
            outboxService.saveEvent(
                    order.getId().toString(),
                    "Order",
                    "OrderApproved",
                    event,
                    orderEventsTopic,
                    order.getId().toString()
            );
            
            log.info("주문 승인 이벤트 저장 완료: orderId={}", order.getId());
            
        } catch (Exception e) {
            log.error("주문 승인 이벤트 저장 실패: orderId={}, error={}", 
                    order.getId(), e.getMessage(), e);
            throw new OrderException(OrderErrorCode.INTERNAL_ERROR);
        }
    }

    @Transactional
    public void publishOrderCanceled(Order order, String reason) {
        try {
            OrderEvents.OrderCanceled event = orderEventConverter.toOrderCanceledEvent(order, reason);
            
            outboxService.saveEvent(
                    order.getId().toString(),
                    "Order",
                    "OrderCanceled",
                    event,
                    orderEventsTopic,
                    order.getId().toString()
            );
            
            log.info("주문 취소 이벤트 저장 완료: orderId={}, reason={}", order.getId(), reason);
            
        } catch (Exception e) {
            log.error("주문 취소 이벤트 저장 실패: orderId={}, error={}", 
                    order.getId(), e.getMessage(), e);
            throw new OrderException(OrderErrorCode.INTERNAL_ERROR);
        }
    }

    @Transactional
    public void publishOrderFailed(Order order, String reason) {
        try {
            OrderEvents.OrderFailed event = orderEventConverter.toOrderFailedEvent(order, reason);
            
            outboxService.saveEvent(
                    order.getId().toString(),
                    "Order",
                    "OrderFailed",
                    event,
                    orderEventsTopic,
                    order.getId().toString()
            );
            
            log.info("주문 실패 이벤트 저장 완료: orderId={}, reason={}", order.getId(), reason);
            
        } catch (Exception e) {
            log.error("주문 실패 이벤트 저장 실패: orderId={}, error={}", 
                    order.getId(), e.getMessage(), e);
            throw new OrderException(OrderErrorCode.INTERNAL_ERROR);
        }
    }
}
