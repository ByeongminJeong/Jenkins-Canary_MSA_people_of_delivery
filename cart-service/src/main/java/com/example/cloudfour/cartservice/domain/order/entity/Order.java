package com.example.cloudfour.cartservice.domain.order.entity;

import com.example.cloudfour.cartservice.domain.order.enums.OrderStatus;
import com.example.cloudfour.cartservice.domain.order.enums.OrderType;
import com.example.cloudfour.cartservice.domain.order.enums.ReceiptType;
import com.example.cloudfour.cartservice.domain.order.exception.OrderErrorCode;
import com.example.cloudfour.cartservice.domain.order.exception.OrderException;
import com.example.cloudfour.modulecommon.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "p_order")
public class Order extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptType receiptType;

    @Column(nullable = false)
    private String address;

    @Column(length = 500)
    private String request;

    @Column(nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    @Builder.Default
    private boolean userIsDeleted = false;

    @Column(name = "userId", nullable = false)
    private UUID user;

    @Column(name = "storeId", nullable = false)
    private UUID store;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    public void updateOrderStatus(OrderStatus orderStatus) {
        if (orderStatus == null) {
            throw new OrderException(OrderErrorCode.INVALID_INPUT);
        }
        this.status = orderStatus;
    }

    public void addOrderItem(OrderItem orderItem) {
        if (orderItem == null) {
            throw new OrderException(OrderErrorCode.INVALID_INPUT);
        }
        orderItem.setOrder(this);
        this.orderItems.add(orderItem);
    }

    public void removeOrderItem(UUID orderItemId) {
        if (orderItemId == null) {
            throw new OrderException(OrderErrorCode.INVALID_INPUT);
        }
        this.orderItems.removeIf(item -> item.getId().equals(orderItemId));
    }

    public int getItemCount() {
        return this.orderItems.size();
    }

    public boolean isCompleted() {
        return this.status == OrderStatus.DELIVERED;
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.ORDER_CANCELED;
    }

    public boolean isInProgress() {
        return this.status != OrderStatus.DELIVERED && this.status != OrderStatus.ORDER_CANCELED;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }


    public void setUser(UUID user) {
        if (user == null) {
            throw new OrderException(OrderErrorCode.INVALID_INPUT);
        }
        this.user = user;
    }

    public void setStore(UUID store) {
        if (store == null) {
            throw new OrderException(OrderErrorCode.INVALID_INPUT);
        }
        this.store = store;
    }

    public static class OrderBuilder {
        private OrderBuilder id(UUID id) {
            throw new OrderException(OrderErrorCode.CREATE_FAILED);
        }
    }
}
