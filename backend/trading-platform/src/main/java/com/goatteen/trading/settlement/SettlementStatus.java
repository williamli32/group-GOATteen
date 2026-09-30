package com.goatteen.trading.settlement;

// Lifecycle: INITIATED → CASH_DEBITED → POSITION_CREDITED → COMPLETED

public enum SettlementStatus {
    
    
    // Settlement has been initiated but not yet processed
    INITIATED,
    
    // Cash has been successfully debited from account
    // Position credit still pending
    CASH_DEBITED,
    
    // Position has been credited to account
    // Awaiting final completion
    POSITION_CREDITED,
    
    // Settlement complete - all steps done, terminal state
    COMPLETED,
    
    // Settlement failed at some step
    // Can retry or escalate
    FAILED;

    
    // Check if this is a terminal state (no further transitions possible)
     
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}