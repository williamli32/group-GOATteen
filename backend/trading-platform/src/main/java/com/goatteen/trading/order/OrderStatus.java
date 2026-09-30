package com.goatteen.trading.order;

public enum OrderStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED,
    FILLED,
    SETTLING, // settling includes: debit the cash , give back the shares to client, update the order statusto SETTLED
    SETTLING_FAILED, // need for when SETTLING fails halfway (ex: cash has been transferred but client hasn't got their shares yet)
    SETTLED // The final status after a sucessful settlement
}
