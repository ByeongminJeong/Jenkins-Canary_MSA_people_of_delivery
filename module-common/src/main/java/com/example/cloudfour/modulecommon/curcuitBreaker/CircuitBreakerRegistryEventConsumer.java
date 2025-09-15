package com.example.cloudfour.modulecommon.curcuitBreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component("customCircuitBreakerEventConsumer")
@ConditionalOnClass(CircuitBreaker.class)
public class CircuitBreakerRegistryEventConsumer implements RegistryEventConsumer<CircuitBreaker> {

    private final String serviceName;

    public CircuitBreakerRegistryEventConsumer(Environment env) {
        this.serviceName = env.getProperty("spring.application.name", "unknown-service");
    }

    @Override
    public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
        String circuitBreakerName = entryAddedEvent.getAddedEntry().getName();

        log.info("[{}] Circuit Breaker '{}' 등록됨", serviceName, circuitBreakerName);

        entryAddedEvent.getAddedEntry().getEventPublisher()
                .onFailureRateExceeded(event ->
                        log.warn("[{}] Circuit Breaker '{}' 실패율 임계값 초과: {}%",
                                serviceName, event.getCircuitBreakerName(), event.getFailureRate())
                )
                .onSlowCallRateExceeded(event ->
                        log.warn("[{}] Circuit Breaker '{}' 슬로우 콜 비율 초과: {}%",
                                serviceName, event.getCircuitBreakerName(), event.getSlowCallRate())
                )
                .onError(event ->
                        log.error("[{}] Circuit Breaker '{}' 에러 발생: {}",
                                serviceName, event.getCircuitBreakerName(),
                                event.getThrowable().getMessage())
                )
                .onSuccess(event ->
                        log.debug("[{}] Circuit Breaker '{}' 호출 성공 (처리시간: {}ms)",
                                serviceName, event.getCircuitBreakerName(),
                                event.getElapsedDuration().toMillis())
                )
                .onStateTransition(event ->
                        log.info("[{}] Circuit Breaker '{}' 상태 변경: {} -> {}",
                                serviceName, event.getCircuitBreakerName(),
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState())
                )
                .onCallNotPermitted(event ->
                        log.warn("[{}] Circuit Breaker '{}' OPEN 상태로 인한 호출 차단",
                                serviceName, event.getCircuitBreakerName())
                );
    }

    @Override
    public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {

    }

    @Override
    public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {

    }
}
