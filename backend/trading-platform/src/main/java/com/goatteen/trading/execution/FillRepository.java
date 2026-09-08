package com.goatteen.trading.execution;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface FillRepository
        extends JpaRepository<Fill, Long> {


    Optional<Fill> findByOrderId(Long orderId);

}
