package com.example.cloudfour.modulecommon.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {
    
    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void scheduleOrderTimeout(String orderId, int timeoutMinutes, Runnable task) {
        cancelOrderTimeout(orderId);
        
        Instant timeoutTime = Instant.now().plus(Duration.ofMinutes(timeoutMinutes));
        ScheduledFuture<?> scheduledTask = taskScheduler.schedule(task, timeoutTime);
        
        scheduledTasks.put(orderId, scheduledTask);
        
        log.info("주문 타임아웃 스케줄 등록: orderId={}, timeoutMinutes={}, scheduledAt={}", 
                orderId, timeoutMinutes, timeoutTime);
    }

    public void scheduleOrderTimeout(String orderId, double timeoutMinutes, Runnable task) {
        cancelOrderTimeout(orderId);
        
        Instant timeoutTime = Instant.now().plus(Duration.ofMillis((long) (timeoutMinutes * 60 * 1000)));
        ScheduledFuture<?> scheduledTask = taskScheduler.schedule(task, timeoutTime);
        
        scheduledTasks.put(orderId, scheduledTask);
        
        log.info("주문 타임아웃 스케줄 등록: orderId={}, timeoutMinutes={}, scheduledAt={}", 
                orderId, timeoutMinutes, timeoutTime);
    }

    public void cancelOrderTimeout(String orderId) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.remove(orderId);
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
            log.info("주문 타임아웃 스케줄 취소: orderId={}", orderId);
        }
    }

    public void shutdown() {
        scheduledTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        scheduledTasks.clear();
        log.info("모든 스케줄 정리 완료");
    }

    public int getActiveScheduleCount() {
        return scheduledTasks.size();
    }
}
