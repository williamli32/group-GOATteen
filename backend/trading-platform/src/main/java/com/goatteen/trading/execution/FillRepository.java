package com.goatteen.trading.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FillRepository
        extends JpaRepository<Fill, Long> {

    Optional<Fill> findByOrderId(Long orderId);

<<<<<<< HEAD
    Optional<Fill> findByIdempotencyKey(String idempotencyKey);
=======
    Optional<Fill> findByIdempotencyKey(
            String idempotencyKey);
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4

}
