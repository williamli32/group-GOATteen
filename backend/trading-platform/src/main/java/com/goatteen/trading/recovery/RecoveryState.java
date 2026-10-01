package com.goatteen.trading.recovery;

public enum RecoveryState {

    /*
     * Order was submitted but never accepted/executed.
     * Recovery must not bypass normal business validation.
     */
    SUBMITTED_NOT_EXECUTED,

    /*
     * Order was accepted but execution never committed.
     * Safe to retry execution.
     */
    ACCEPTED_NOT_EXECUTED,

    /*
     * Complete persisted Fill/cash/position/audit state exists.
     * Never settle again.
     */
    FULLY_EXECUTED,

    /*
     * Order was rejected and therefore must never execute.
     */
    REJECTED,

    /*
     * Persisted records contradict one another.
     * Recovery must never guess.
     */
    AMBIGUOUS
}