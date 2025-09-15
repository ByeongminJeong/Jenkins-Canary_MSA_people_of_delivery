package com.example.cloudfour.cartservice.domain.order.service.command;

import com.example.cloudfour.cartservice.domain.order.entity.Order;
import com.example.cloudfour.cartservice.domain.order.enums.OrderStatus;
import com.example.cloudfour.cartservice.domain.order.exception.OrderErrorCode;
import com.example.cloudfour.cartservice.domain.order.exception.OrderException;
import com.example.cloudfour.cartservice.domain.order.repository.OrderRepository;
import com.example.cloudfour.cartservice.domain.order.service.event.OrderEventService;
import com.example.cloudfour.modulecommon.messaging.SagaAwareDLQHandler;
import com.example.cloudfour.modulecommon.messaging.Envelope;
import com.example.cloudfour.modulecommon.messaging.MessageConsumer;
import com.example.cloudfour.modulecommon.messaging.order.OrderCommands;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCommandHandler {
    
    private final OrderRepository orderRepository;
    private final OrderEventService orderEventService;
    private final MessageConsumer messageConsumer;
    private final ObjectMapper objectMapper;
    private final SagaAwareDLQHandler sagaAwareDLQHandler;

    @KafkaListener(topics = "${kafka.topics.orderCommands:order.commands.v1}", 
                   groupId = "order-command-handler")
    @Transactional
    public void handleOrderCommand(
            @Payload Envelope<Object> envelope,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            messageConsumer.logMessageReceived(envelope, topic, partition, offset, key);
            
            Object payload = envelope.getPayload();

            if (payload instanceof Map) {
                payload = convertLinkedHashMapToCommand((Map<String, Object>) payload, envelope.getMeta().getType());
            }
            
            if (payload instanceof OrderCommands.ApproveOrder) {
                handleApproveOrder((OrderCommands.ApproveOrder) payload, acknowledgment);
            } else if (payload instanceof OrderCommands.CancelOrder) {
                handleCancelOrder((OrderCommands.CancelOrder) payload, acknowledgment);
            } else {
                log.warn("알 수 없는 주문 커맨드 타입: {}", payload.getClass().getSimpleName());
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            messageConsumer.logMessageProcessingError(
                    topic, key, envelope.getMeta().getMsgId(), 
                    envelope.getMeta().getSagaId(), 
                    envelope.getMeta().getType(), e);
            
            log.error("주문 커맨드 처리 실패: error={}", e.getMessage(), e);

            sagaAwareDLQHandler.handleSagaFailure(topic, key, envelope, e, acknowledgment);
        }
    }
    
    private void handleApproveOrder(OrderCommands.ApproveOrder command, Acknowledgment acknowledgment) {
        try {
            UUID orderId = command.getOrderId();
            
            log.info("주문 승인 커맨드 처리 시작: orderId={}", orderId);
            
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderException(OrderErrorCode.NOT_FOUND));

            order.updateOrderStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);

            orderEventService.publishOrderApproved(order);
            
            log.info("주문 승인 처리 완료: orderId={}", orderId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("주문 승인 커맨드 처리 실패: orderId={}, error={}", 
                    command.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
    
    private void handleCancelOrder(OrderCommands.CancelOrder command, Acknowledgment acknowledgment) {
        try {
            UUID orderId = command.getOrderId();
            String reason = command.getReason();
            
            log.info("주문 취소 커맨드 처리 시작: orderId={}, reason={}", orderId, reason);
            
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderException(OrderErrorCode.NOT_FOUND));

            order.updateOrderStatus(OrderStatus.ORDER_CANCELED);
            orderRepository.save(order);

            orderEventService.publishOrderCanceled(order, reason);
            
            log.info("주문 취소 처리 완료: orderId={}, reason={}", orderId, reason);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("주문 취소 커맨드 처리 실패: orderId={}, error={}", 
                    command.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
    
    private Object convertLinkedHashMapToCommand(Map<String, Object> map, String type) {
        try {
            if ("ApproveOrder".equals(type)) {
                return objectMapper.convertValue(map, OrderCommands.ApproveOrder.class);
            } else if ("CancelOrder".equals(type)) {
                return objectMapper.convertValue(map, OrderCommands.CancelOrder.class);
            }
            return map;
        } catch (Exception e) {
            log.error("LinkedHashMap을 커맨드로 변환 실패: type={}, error={}", type, e.getMessage(), e);
            return map;
        }
    }
}