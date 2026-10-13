package com.goatteen.trading.reporting.tracking;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Audit trail for reporting sync attempts.
 
// Used to detect and recover from failed syncs during replay.
 
@Entity
@Table(name = "sync_tracking", schema = "reporting")
public class SyncTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long syncId;

    @Column(nullable = false, unique = true)
    private Long fillId;

    @Column(nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(nullable = false)
    private LocalDateTime syncCompletedAt;

    @Column(nullable = false, length = 20)
    private String syncStatus;  // SUCCESS, FAILED, PENDING

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected SyncTracking() {
    }

    public SyncTracking(
            Long fillId,
            LocalDateTime eventTimestamp,
            String syncStatus,
            String errorMessage) {

        this.fillId = fillId;
        this.eventTimestamp = eventTimestamp;
        this.syncStatus = syncStatus;
        this.errorMessage = errorMessage;
        this.syncCompletedAt = LocalDateTime.now();
    }

    // Getters
    public Long getSyncId() { return syncId; }
    public Long getFillId() { return fillId; }
    public LocalDateTime getEventTimestamp() { return eventTimestamp; }
    public LocalDateTime getSyncCompletedAt() { return syncCompletedAt; }
    public String getSyncStatus() { return syncStatus; }
    public String getErrorMessage() { return errorMessage; }
}