package com.example.cloudfour.paymentservice.domain.payment.service;

import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentException;
import com.example.cloudfour.paymentservice.domain.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String PAYMENT_KEY_PREFIX = "payment:orderId:";
    private static final String ORDER_KEY_PREFIX = "payment:paymentKey:";
    private static final Duration CACHE_EXPIRY = Duration.ofMinutes(10); // 토스페이먼츠 10분 제한

    public void savePaymentMapping(String orderId, String paymentKey) {
        try {
            String orderKey = PAYMENT_KEY_PREFIX + orderId;
            String paymentKeyCache = ORDER_KEY_PREFIX + paymentKey;
            
            redisTemplate.opsForValue().set(orderKey, paymentKey, CACHE_EXPIRY);
            redisTemplate.opsForValue().set(paymentKeyCache, orderId, CACHE_EXPIRY);
            
            log.info("결제 매핑 저장 완료: orderId={}, paymentKey=***{}", orderId, 
                    paymentKey.substring(Math.max(0, paymentKey.length()-4)));
        } catch (Exception e) {
            log.error("결제 매핑 저장 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new PaymentException(PaymentErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String getPaymentKeyByOrderId(String orderId) {
        try {
            String orderKey = PAYMENT_KEY_PREFIX + orderId;
            Object paymentKey = redisTemplate.opsForValue().get(orderKey);
            
            if (paymentKey == null) {
                log.warn("결제 매핑을 찾을 수 없음: orderId={}", orderId);
                return null;
            }
            
            log.info("결제 매핑 조회 성공: orderId={}, paymentKey=***{}", orderId, 
                    paymentKey.toString().substring(Math.max(0, paymentKey.toString().length()-4)));
            return paymentKey.toString();
        } catch (Exception e) {
            log.error("결제 매핑 조회 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            return null;
        }
    }

    public String getOrderIdByPaymentKey(String paymentKey) {
        try {
            String paymentKeyCache = ORDER_KEY_PREFIX + paymentKey;
            Object orderId = redisTemplate.opsForValue().get(paymentKeyCache);
            
            if (orderId == null) {
                log.warn("결제 매핑을 찾을 수 없음: paymentKey=***{}", 
                        paymentKey.substring(Math.max(0, paymentKey.length()-4)));
                return null;
            }
            
            log.info("결제 매핑 조회 성공: paymentKey=***{}, orderId={}", 
                    paymentKey.substring(Math.max(0, paymentKey.length()-4)), orderId);
            return orderId.toString();
        } catch (Exception e) {
            log.error("결제 매핑 조회 실패: paymentKey=***{}, error={}", 
                    paymentKey.substring(Math.max(0, paymentKey.length()-4)), e.getMessage(), e);
            return null;
        }
    }

    public void deletePaymentMapping(String orderId, String paymentKey) {
        try {
            String orderKey = PAYMENT_KEY_PREFIX + orderId;
            String paymentKeyCache = ORDER_KEY_PREFIX + paymentKey;
            
            redisTemplate.delete(orderKey);
            redisTemplate.delete(paymentKeyCache);
            
            log.info("결제 매핑 삭제 완료: orderId={}, paymentKey=***{}", orderId, 
                    paymentKey.substring(Math.max(0, paymentKey.length()-4)));
        } catch (Exception e) {
            log.error("결제 매핑 삭제 실패: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }
}
