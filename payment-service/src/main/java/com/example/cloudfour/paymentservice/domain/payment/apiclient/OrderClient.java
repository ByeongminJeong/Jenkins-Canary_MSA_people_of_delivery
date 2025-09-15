package com.example.cloudfour.paymentservice.domain.payment.apiclient;

import com.example.cloudfour.paymentservice.commondto.OrderResponseDTO;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentException;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentErrorCode;
// import com.example.cloudfour.paymentservice.domain.payment.dto.OrderStatusUpdateRequestDTO; // 이벤트로 처리됨
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderClient {

    private final OrderFeignClient orderClient;

    public OrderResponseDTO getOrderById(String orderId, UUID userId) {
        try {
            log.info("주문 정보 조회 요청: orderId={}, userId={}", orderId, userId);

            OrderResponseDTO order = orderClient.getOrderById(orderId, userId);
            log.info("주문 정보 조회 성공: orderId={}, userId={}", orderId, userId);
            
            return order;
        } catch (Exception e) {
            log.error("주문 정보 조회 실패: orderId={}, userId={}, error={}", orderId, userId, e.getMessage());
            throw new PaymentException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
    }
}

