package com.example.cloudfour.paymentservice.domain.payment.repository;

import com.example.cloudfour.paymentservice.domain.payment.entity.Payment;
import com.example.cloudfour.paymentservice.domain.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentKey(String paymentKey);
    
    Optional<Payment> findByOrderId(UUID orderId);
    
    Optional<Payment> findByOrderIdAndUserId(UUID orderId, UUID userId);
    
    List<Payment> findAllByUserId(UUID userId);
    
    List<Payment> findAllByUserIdAndPaymentStatus(UUID userId, PaymentStatus status);
    
    @Query("SELECT p FROM Payment p WHERE p.idempotencyKey = :idempotencyKey")
    Optional<Payment> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    
    @Query("SELECT p FROM Payment p WHERE p.paymentKey = :paymentKey AND p.orderId = :orderId")
    Optional<Payment> findByPaymentKeyAndOrderId(@Param("paymentKey") String paymentKey, @Param("orderId") UUID orderId);
    
    boolean existsByPaymentKey(String paymentKey);
    
    boolean existsByOrderId(UUID orderId);
    
    boolean existsByOrderIdAndUserId(UUID orderId, UUID userId);
}
