package com.example.cloudfour.modulecommon.outbox.repository;

import com.example.cloudfour.modulecommon.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.createdAt < :before ORDER BY e.createdAt ASC")
    List<OutboxEvent> findFailedEventsBefore(@Param("before") Instant before);

    List<OutboxEvent> findByAggregateIdAndAggregateTypeOrderByCreatedAtAsc(String aggregateId, String aggregateType);

    @Query("SELECT e FROM OutboxEvent e WHERE e.aggregateId = :aggregateId AND e.aggregateType = :aggregateType AND e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEventsByAggregate(@Param("aggregateId") String aggregateId, @Param("aggregateType") String aggregateType);
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.aggregateId = :aggregateId AND e.eventType = :eventType ORDER BY e.createdAt ASC")
    List<OutboxEvent> findByAggregateIdAndEventType(@Param("aggregateId") String aggregateId, @Param("eventType") String eventType);
}
