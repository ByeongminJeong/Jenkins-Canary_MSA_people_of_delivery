package com.example.cloudfour.modulecommon.saga.entity;

import com.example.cloudfour.modulecommon.saga.enums.SagaStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_states")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String sagaId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;
    
    @Column(nullable = false)
    private String sagaType;
    
    @Column(columnDefinition = "TEXT")
    private String sagaData;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant updatedAt;
    
    private Instant completedAt;
    
    private String lastProcessedMsgId;
    
    private String errorMessage;
    
    public void updateStatus(SagaStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
        
        if (newStatus == SagaStatus.COMPLETED || newStatus == SagaStatus.COMPENSATED) {
            this.completedAt = Instant.now();
        }
    }
    
    public void setError(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = SagaStatus.FAILED;
        this.updatedAt = Instant.now();
    }
    
    public void updateLastProcessedMsgId(String msgId) {
        this.lastProcessedMsgId = msgId;
        this.updatedAt = Instant.now();
    }
}
