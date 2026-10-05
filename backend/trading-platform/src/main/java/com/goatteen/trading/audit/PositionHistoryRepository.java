package com.goatteen.trading.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
<<<<<<< HEAD
public interface PositionHistoryRepository extends JpaRepository<PositionHistory, Long> {
    Optional<PositionHistory> findByFillId(Long fillId);
}
=======
public interface PositionHistoryRepository
        extends JpaRepository<PositionHistory, Long> {

    Optional<PositionHistory> findByFillId(Long fillId);

    Optional<PositionHistory>
            findFirstByAccountIdAndInstrumentIdOrderByRecordedAtDescIdDesc(
                    Long accountId,
                    Long instrumentId);
}
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
