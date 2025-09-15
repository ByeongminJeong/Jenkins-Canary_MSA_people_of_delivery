package com.example.cloudfour.modulecommon.saga.service;

import com.example.cloudfour.modulecommon.converter.InventoryCommandConverter;
import com.example.cloudfour.modulecommon.converter.OrderCommandConverter;
import com.example.cloudfour.modulecommon.converter.PaymentCommandConverter;
import com.example.cloudfour.modulecommon.converter.SagaDataConverter;
import com.example.cloudfour.modulecommon.messaging.MessagePublisher;
import com.example.cloudfour.modulecommon.messaging.inventory.InventoryCommands;
import com.example.cloudfour.modulecommon.messaging.order.OrderCommands;
import com.example.cloudfour.modulecommon.messaging.payment.PaymentCommands;
import com.example.cloudfour.modulecommon.saga.entity.SagaState;
import com.example.cloudfour.modulecommon.saga.enums.SagaStatus;
import com.example.cloudfour.modulecommon.saga.repository.SagaStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {
    
    private final SagaStateRepository sagaStateRepository;
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void startOrderSaga(String orderId, String userId, String storeId, String sagaData) {
        try {
            Optional<SagaState> existingSaga = sagaStateRepository.findBySagaId(orderId);
            if (existingSaga.isPresent()) {
                log.warn("이미 존재하는 Saga입니다: orderId={}, status={}", orderId, existingSaga.get().getStatus());
                return;
            }
            
            SagaState sagaState = SagaState.builder()
                    .sagaId(orderId)
                    .status(SagaStatus.STARTED)
                    .sagaType("ORDER_SAGA")
                    .sagaData(sagaData)
                    .createdAt(Instant.now())
                    .build();
            
            sagaStateRepository.save(sagaState);

            List<InventoryCommands.ReserveInventory.ReserveItem> reserveItems = SagaDataConverter.parseOrderItemsFromSagaData(sagaData);
            InventoryCommands.ReserveInventory reserveCommand = InventoryCommandConverter.toReserveInventoryCommand(orderId, storeId, reserveItems);
            
            messagePublisher.publishCommand(
                    "inventory.commands.v1",
                    orderId,
                    reserveCommand,
                    "order-saga-orchestrator",
                    orderId,
                    null
            );
            
            log.info("주문 사가 시작: orderId={}, userId={}, storeId={}, items={}", orderId, userId, storeId, reserveItems.size());
            
        } catch (Exception e) {
            log.error("주문 사가 시작 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new RuntimeException("주문 사가 시작 실패", e);
        }
    }

    @Transactional
    public void handleInventoryReserved(String orderId, String msgId) {
        try {
            SagaState sagaState = sagaStateRepository.findBySagaId(orderId)
                    .orElseThrow(() -> new RuntimeException("사가 상태를 찾을 수 없습니다: " + orderId));
            
            if (sagaState.getStatus() != SagaStatus.STARTED) {
                log.warn("사가 상태가 예상과 다름: orderId={}, expected=STARTED, actual={}", 
                        orderId, sagaState.getStatus());
                return;
            }
            sagaState.updateStatus(SagaStatus.IN_PROGRESS);
            sagaState.updateLastProcessedMsgId(msgId);
            sagaStateRepository.save(sagaState);

            BigDecimal amount = extractAmountFromSagaData(sagaState.getSagaData());

            createPaymentRecord(orderId, sagaState.getSagaData(), amount);
            
            log.info("재고 예약 성공 처리 완료: orderId={}", orderId);
            
        } catch (Exception e) {
            log.error("재고 예약 성공 처리 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            handleSagaFailure(orderId, "재고 예약 성공 처리 실패: " + e.getMessage());
        }
    }

    @Transactional
    public void handleInventoryReservationFailed(String orderId, String msgId, String reason) {
        try {
            SagaState sagaState = sagaStateRepository.findBySagaId(orderId)
                    .orElseThrow(() -> new RuntimeException("사가 상태를 찾을 수 없습니다: " + orderId));

            String storeId = SagaDataConverter.extractStoreIdFromSagaData(sagaState.getSagaData());
            String userId = SagaDataConverter.extractUserIdFromSagaData(sagaState.getSagaData());
            
            OrderCommands.CancelOrder cancelCommand = OrderCommandConverter.toCancelOrderCommand(orderId, userId, storeId, "재고 부족: " + reason);
            
            messagePublisher.publishCommand(
                    "order.commands.v1",
                    orderId,
                    cancelCommand,
                    "order-saga-orchestrator",
                    orderId,
                    msgId
            );

            handleSagaFailure(orderId, "재고 예약 실패: " + reason);
            
            log.info("재고 예약 실패 처리 완료: orderId={}, reason={}", orderId, reason);
            
        } catch (Exception e) {
            log.error("재고 예약 실패 처리 중 오류: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    @Transactional
    public void handlePaymentAuthorized(String orderId, String msgId) {
        try {
            SagaState sagaState = sagaStateRepository.findBySagaId(orderId)
                    .orElseThrow(() -> new RuntimeException("사가 상태를 찾을 수 없습니다: " + orderId));

            String userId = SagaDataConverter.extractUserIdFromSagaData(sagaState.getSagaData());
            String storeId = SagaDataConverter.extractStoreIdFromSagaData(sagaState.getSagaData());
            OrderCommands.ApproveOrder approveCommand = OrderCommandConverter.toApproveOrderCommand(orderId, userId, storeId);
            
            messagePublisher.publishCommand(
                    "order.commands.v1",
                    orderId,
                    approveCommand,
                    "order-saga-orchestrator",
                    orderId,
                    msgId
            );

            sagaState.updateStatus(SagaStatus.COMPLETED);
            sagaState.updateLastProcessedMsgId(msgId);
            sagaStateRepository.save(sagaState);
            
            log.info("주문 사가 완료: orderId={}", orderId);
            
        } catch (Exception e) {
            log.error("결제 승인 성공 처리 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            handleSagaFailure(orderId, "결제 승인 성공 처리 실패: " + e.getMessage());
        }
    }

    @Transactional
    public void handlePaymentFailed(String orderId, String msgId, String reason) {
        try {
            SagaState sagaState = sagaStateRepository.findBySagaId(orderId)
                    .orElseThrow(() -> new RuntimeException("사가 상태를 찾을 수 없습니다: " + orderId));

            List<InventoryCommands.ReleaseInventory.ReleaseItem> releaseItems = SagaDataConverter.parseOrderItemsFromSagaDataForRelease(sagaState.getSagaData());
            String storeId = SagaDataConverter.extractStoreIdFromSagaData(sagaState.getSagaData());
            
            InventoryCommands.ReleaseInventory releaseCommand = InventoryCommandConverter.toReleaseInventoryCommand(orderId, storeId, releaseItems);
            
            messagePublisher.publishCommand(
                    "inventory.commands.v1",
                    orderId,
                    releaseCommand,
                    "order-saga-orchestrator",
                    orderId,
                    msgId
            );

            String userId = SagaDataConverter.extractUserIdFromSagaData(sagaState.getSagaData());
            
            OrderCommands.CancelOrder cancelCommand = OrderCommandConverter.toCancelOrderCommand(orderId, userId, storeId, "결제 실패: " + reason);
            
            messagePublisher.publishCommand(
                    "order.commands.v1",
                    orderId,
                    cancelCommand,
                    "order-saga-orchestrator",
                    orderId,
                    msgId
            );

            handleSagaFailure(orderId, "결제 실패: " + reason);
            
            log.info("결제 실패 처리 완료: orderId={}, reason={}", orderId, reason);
            
        } catch (Exception e) {
            log.error("결제 실패 처리 중 오류: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    private void handleSagaFailure(String orderId, String reason) {
        try {
            SagaState sagaState = sagaStateRepository.findBySagaId(orderId)
                    .orElseThrow(() -> new RuntimeException("사가 상태를 찾을 수 없습니다: " + orderId));
            
            sagaState.setError(reason);
            sagaStateRepository.save(sagaState);
            
            log.error("사가 실패: orderId={}, reason={}", orderId, reason);
            
        } catch (Exception e) {
            log.error("사가 실패 처리 중 오류: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractAmountFromSagaData(String sagaData) {
        try {
            Map<String, Object> data = objectMapper.readValue(sagaData, Map.class);
            Object amount = data.get("amount");
            if (amount instanceof Number) {
                return BigDecimal.valueOf(((Number) amount).doubleValue());
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("sagaData에서 금액 추출 실패: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private void createPaymentRecord(String orderId, String sagaData, BigDecimal amount) {
        try {
            String userId = SagaDataConverter.extractUserIdFromSagaData(sagaData);
            String storeId = SagaDataConverter.extractStoreIdFromSagaData(sagaData);
            
            log.info("결제 정보 저장 시작: orderId={}, userId={}, storeId={}, amount={}", 
                    orderId, userId, storeId, amount);

            // 1. 결제 정보만 저장 (토스 결제창은 프론트엔드에서 직접 호출)
            PaymentCommands.CreatePayment createPaymentCommand = PaymentCommandConverter.toCreatePaymentCommand(orderId, userId, storeId, amount);
            
            messagePublisher.publishCommand(
                    "payment.commands.v1",
                    orderId,
                    createPaymentCommand,
                    "order-saga-orchestrator",
                    orderId,
                    null
            );

            log.info("결제 정보 저장 완료: orderId={} - 프론트엔드에서 토스 결제창 호출 필요", orderId);
            
        } catch (Exception e) {
            log.error("결제 정보 저장 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            handleSagaFailure(orderId, "결제 정보 저장 실패: " + e.getMessage());
        }
    }
}
