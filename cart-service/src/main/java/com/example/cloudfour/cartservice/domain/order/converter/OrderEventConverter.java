package com.example.cloudfour.cartservice.domain.order.converter;

import com.example.cloudfour.cartservice.domain.order.entity.Order;
import com.example.cloudfour.cartservice.domain.order.entity.OrderItem;
import com.example.cloudfour.cartservice.domain.order.entity.OrderItemOption;
import com.example.cloudfour.modulecommon.messaging.order.OrderEvents;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderEventConverter {

    public OrderEvents.OrderCreated toOrderCreatedEvent(Order order) {
        return OrderEvents.OrderCreated.builder()
                .orderId(order.getId())
                .userId(order.getUser())
                .storeId(order.getStore())
                .totalAmount(BigDecimal.valueOf(order.getTotalPrice()))
                .orderStatus(order.getStatus().name())
                .deliveryAddress(order.getAddress())
                .orderItems(convertOrderItems(order.getOrderItems()))
                .createdAt(Instant.now())
                .build();
    }

    public OrderEvents.OrderApproved toOrderApprovedEvent(Order order) {
        return OrderEvents.OrderApproved.builder()
                .orderId(order.getId())
                .userId(order.getUser())
                .storeId(order.getStore())
                .approvedAt(Instant.now())
                .build();
    }

    public OrderEvents.OrderCanceled toOrderCanceledEvent(Order order, String reason) {
        return OrderEvents.OrderCanceled.builder()
                .orderId(order.getId())
                .userId(order.getUser())
                .storeId(order.getStore())
                .reason(reason)
                .canceledAt(Instant.now())
                .build();
    }

    public OrderEvents.OrderFailed toOrderFailedEvent(Order order, String reason) {
        return OrderEvents.OrderFailed.builder()
                .orderId(order.getId())
                .userId(order.getUser())
                .storeId(order.getStore())
                .reason(reason)
                .failedAt(Instant.now())
                .build();
    }

    private List<OrderEvents.OrderCreated.OrderItem> convertOrderItems(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(this::convertOrderItem)
                .collect(Collectors.toList());
    }

    private OrderEvents.OrderCreated.OrderItem convertOrderItem(OrderItem orderItem) {
        return OrderEvents.OrderCreated.OrderItem.builder()
                .menuId(orderItem.getMenu())
                .quantity(orderItem.getQuantity())
                .price(BigDecimal.valueOf(orderItem.getPrice()))
                .options(convertOrderItemOptions(orderItem.getOptions()))
                .build();
    }

    private List<OrderEvents.OrderCreated.OrderItemOption> convertOrderItemOptions(
            List<OrderItemOption> options) {
        return options.stream()
                .map(option -> OrderEvents.OrderCreated.OrderItemOption.builder()
                        .optionId(option.getId())
                        .optionName(option.getOptionName())
                        .optionPrice(BigDecimal.valueOf(option.getAdditionalPrice()))
                        .build())
                .collect(Collectors.toList());
    }
}
