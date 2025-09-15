package com.example.cloudfour.cartservice.domain.order.dto;

import com.example.cloudfour.cartservice.domain.order.enums.OrderStatus;
import com.example.cloudfour.cartservice.domain.order.enums.OrderType;
import com.example.cloudfour.cartservice.domain.order.enums.ReceiptType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

public class OrderRequestDTO {
    
    @Getter
    @Builder
    @Jacksonized
    public static class OrderCreateRequestDTO {
        
        @NotNull(message = "주문 타입은 필수입니다")
        private OrderType orderType;
        
        @NotNull(message = "주문 상태는 필수입니다")
        private OrderStatus orderStatus;
        
        @NotNull(message = "수령 타입은 필수입니다")
        private ReceiptType receiptType;
        
        @Size(max = 500, message = "요청사항은 500자 이하여야 합니다")
        private String request;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class OrderUpdateRequestDTO {
        
        @NotNull(message = "새로운 주문 상태는 필수입니다")
        private OrderStatus newStatus;
    }
}
