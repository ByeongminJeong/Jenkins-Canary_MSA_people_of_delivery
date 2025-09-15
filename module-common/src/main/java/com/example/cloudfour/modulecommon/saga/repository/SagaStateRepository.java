package com.example.cloudfour.modulecommon.saga.repository;

import com.example.cloudfour.modulecommon.saga.entity.SagaState;
import com.example.cloudfour.modulecommon.saga.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {

    Optional<SagaState> findBySagaId(String sagaId);

    List<SagaState> findByStatus(SagaStatus status);

    @Query("SELECT s FROM SagaState s WHERE s.status IN ('STARTED', 'IN_PROGRESS') AND s.createdAt < :before")
    List<SagaState> findIncompleteSagasBefore(@Param("before") Instant before);

    @Query("SELECT s FROM SagaState s WHERE s.sagaType = :sagaType AND s.status IN ('STARTED', 'IN_PROGRESS')")
    List<SagaState> findIncompleteSagasByType(@Param("sagaType") String sagaType);
}
