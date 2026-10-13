package com.goatteen.trading.reporting.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncTrackingRepository extends JpaRepository<SyncTracking, Long> {

    
    // Find all failed sync attempts for recovery/replay.
    List<SyncTracking> findBySyncStatus(String status);
}