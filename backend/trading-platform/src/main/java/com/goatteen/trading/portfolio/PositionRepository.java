package com.goatteen.trading.portfolio;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface PositionRepository
        extends JpaRepository<Position, Long> {


    List<Position> findByAccountId(Long accountId);

    Optional<Position> findByAccountIdAndInstrumentId(Long accountId, Long instrumentId);

}
