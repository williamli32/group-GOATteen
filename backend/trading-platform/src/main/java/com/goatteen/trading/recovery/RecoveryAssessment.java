package com.goatteen.trading.recovery;

public record RecoveryAssessment(
        Long orderId,
        RecoveryState state,
        boolean safeToExecute,
        String message) {
}