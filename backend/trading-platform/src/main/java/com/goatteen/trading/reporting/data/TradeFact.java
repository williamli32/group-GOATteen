package com.goatteen.trading.reporting.data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

//  Read-only entity mapped to reporting.trade_facts table.
//  Immutable after creation; never updated by the application.
//  Used only for reporting, analytics, and dashboard queries.
@Entity
@Table(name = "trade_facts", schema = "reporting")
public class TradeFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeFactId;

    @Column(nullable = false, unique = true)
    private Long fillId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime orderSubmittedAt;

    @Column(nullable = true)
    private LocalDateTime orderAcceptedAt;

    @Column(nullable = false)
    private LocalDateTime orderFilledAt;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long clientId;

    @Column(name = "client_first_name")
    private String clientFirstName;

    @Column(name = "client_last_name")
    private String clientLastName;

    @Column(nullable = false)
    private Long instrumentId;

    @Column(name = "instrument_symbol")
    private String instrumentSymbol;

    @Column(name = "instrument_class")
    private String instrumentClass;

    @Column(name = "instrument_name")
    private String instrumentName;

    @Column(name = "currency")
    private String currency;

    @Column(nullable = false, length = 4)
    private String side;  // BUY or SELL

    @Column(nullable = false)
    private BigDecimal orderQuantity;

    @Column(nullable = false)
    private BigDecimal executionPrice;

    @Column(nullable = false)
    private BigDecimal executionQuantity;

    @Column(nullable = false)
    private BigDecimal tradeValue;

    @Column(nullable = false)
    private String orderStatus;  // FILLED or REJECTED

    @Column(name = "bid_price")
    private BigDecimal bidPrice;

    @Column(name = "ask_price")
    private BigDecimal askPrice;

    @Column(name = "quote_timestamp")
    private LocalDateTime quoteTimestamp;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime dataSyncedAt;

    // Constructor for event-driven creation
    public TradeFact() {
    }

    public TradeFact(
            Long fillId,
            Long orderId,
            LocalDateTime orderSubmittedAt,
            LocalDateTime orderAcceptedAt,
            LocalDateTime orderFilledAt,
            Long accountId,
            Long clientId,
            String clientFirstName,
            String clientLastName,
            Long instrumentId,
            String instrumentSymbol,
            String instrumentClass,
            String instrumentName,
            String currency,
            String side,
            BigDecimal orderQuantity,
            BigDecimal executionPrice,
            BigDecimal executionQuantity,
            BigDecimal tradeValue,
            String orderStatus,
            BigDecimal bidPrice,
            BigDecimal askPrice,
            LocalDateTime quoteTimestamp) {

        this.fillId = fillId;
        this.orderId = orderId;
        this.orderSubmittedAt = orderSubmittedAt;
        this.orderAcceptedAt = orderAcceptedAt;
        this.orderFilledAt = orderFilledAt;
        this.accountId = accountId;
        this.clientId = clientId;
        this.clientFirstName = clientFirstName;
        this.clientLastName = clientLastName;
        this.instrumentId = instrumentId;
        this.instrumentSymbol = instrumentSymbol;
        this.instrumentClass = instrumentClass;
        this.instrumentName = instrumentName;
        this.currency = currency;
        this.side = side;
        this.orderQuantity = orderQuantity;
        this.executionPrice = executionPrice;
        this.executionQuantity = executionQuantity;
        this.tradeValue = tradeValue;
        this.orderStatus = orderStatus;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.quoteTimestamp = quoteTimestamp;
        this.createdAt = LocalDateTime.now();
        this.dataSyncedAt = LocalDateTime.now();
    }

	public Long getTradeFactId() {
		return tradeFactId;
	}

	public Long getFillId() {
		return fillId;
	}

	public Long getOrderId() {
		return orderId;
	}

	public LocalDateTime getOrderSubmittedAt() {
		return orderSubmittedAt;
	}

	public LocalDateTime getOrderAcceptedAt() {
		return orderAcceptedAt;
	}

	public LocalDateTime getOrderFilledAt() {
		return orderFilledAt;
	}

	public Long getAccountId() {
		return accountId;
	}

	public Long getClientId() {
		return clientId;
	}

	public String getClientFirstName() {
		return clientFirstName;
	}

	public String getClientLastName() {
		return clientLastName;
	}

	public Long getInstrumentId() {
		return instrumentId;
	}

	public String getInstrumentSymbol() {
		return instrumentSymbol;
	}

	public String getInstrumentClass() {
		return instrumentClass;
	}

	public String getInstrumentName() {
		return instrumentName;
	}

	public String getCurrency() {
		return currency;
	}

	public String getSide() {
		return side;
	}

	public BigDecimal getOrderQuantity() {
		return orderQuantity;
	}

	public BigDecimal getExecutionPrice() {
		return executionPrice;
	}

	public BigDecimal getExecutionQuantity() {
		return executionQuantity;
	}

	public BigDecimal getTradeValue() {
		return tradeValue;
	}

	public String getOrderStatus() {
		return orderStatus;
	}

	public BigDecimal getBidPrice() {
		return bidPrice;
	}

	public BigDecimal getAskPrice() {
		return askPrice;
	}

	public LocalDateTime getQuoteTimestamp() {
		return quoteTimestamp;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getDataSyncedAt() {
		return dataSyncedAt;
	}

}