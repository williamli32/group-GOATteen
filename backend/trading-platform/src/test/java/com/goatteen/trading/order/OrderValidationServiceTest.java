package com.goatteen.trading.order;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.marketdata.QuoteRepository;
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderValidationServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private Account account;

    @Mock
    private Instrument instrument;

    @Mock
    private Quote quote;

    @Mock
    private Position position;

    private OrderValidationService service;

    @BeforeEach
    void setUp() {

        service = new OrderValidationService(
                positionRepository,
                quoteRepository);

        when(instrument.getId())
                .thenReturn(1L);

        when(instrument.isTradable())
                .thenReturn(true);

    }

    @Test
    void shouldRejectBuyWhenQuoteIsMissing() {

        when(
                quoteRepository
                        .findTopByInstrumentIdOrderByQuotedAtDesc(
                                1L))
                .thenReturn(
                        Optional.empty());

        OrderValidationService.ValidationResult result = service.validate(
                account,
                instrument,
                OrderSide.BUY,
                BigDecimal.ONE);

        assertFalse(
                result.isAccepted());

        assertEquals(
                "No market quote available for instrument",
                result.getRejectionReason());
    }

    @Test
    void shouldRejectBuyWhenCurrencyDoesNotMatch() {

        when(
                quoteRepository
                        .findTopByInstrumentIdOrderByQuotedAtDesc(
                                1L))
                .thenReturn(
                        Optional.of(quote));

        when(account.getCurrency())
                .thenReturn("GBP");

        when(instrument.getCurrency())
                .thenReturn("USD");

        OrderValidationService.ValidationResult result = service.validate(
                account,
                instrument,
                OrderSide.BUY,
                BigDecimal.ONE);

        assertFalse(
                result.isAccepted());

        assertEquals(
                "Currency conversion is not supported yet",
                result.getRejectionReason());
    }

    @Test
    void shouldRejectBuyWhenCashIsInsufficient() {

        when(
                quoteRepository
                        .findTopByInstrumentIdOrderByQuotedAtDesc(
                                1L))
                .thenReturn(
                        Optional.of(quote));

        when(account.getCurrency())
                .thenReturn("GBP");

        when(instrument.getCurrency())
                .thenReturn("GBP");

        when(account.getCashBalance())
                .thenReturn(
                        new BigDecimal("100.00"));

        when(quote.getAskPrice())
                .thenReturn(
                        new BigDecimal("75.10"));

        OrderValidationService.ValidationResult result = service.validate(
                account,
                instrument,
                OrderSide.BUY,
                new BigDecimal("2"));

        assertFalse(
                result.isAccepted());

        assertEquals(
                "Insufficient cash balance",
                result.getRejectionReason());
    }

    @Test
    void shouldAcceptBuyWhenCashCoversAskPriceTimesQuantity() {

        when(
                quoteRepository
                        .findTopByInstrumentIdOrderByQuotedAtDesc(
                                1L))
                .thenReturn(
                        Optional.of(quote));

        when(account.getCurrency())
                .thenReturn("GBP");

        when(instrument.getCurrency())
                .thenReturn("GBP");

        when(account.getCashBalance())
                .thenReturn(
                        new BigDecimal("1000.00"));

        when(quote.getAskPrice())
                .thenReturn(
                        new BigDecimal("75.10"));

        OrderValidationService.ValidationResult result = service.validate(
                account,
                instrument,
                OrderSide.BUY,
                new BigDecimal("2"));

        assertTrue(
                result.isAccepted());

        assertNull(
                result.getRejectionReason());
    }

    @Test
    void shouldRejectSellWhenHoldingIsInsufficient() {

        when(
                quoteRepository
                        .findTopByInstrumentIdOrderByQuotedAtDesc(
                                1L))
                .thenReturn(
                        Optional.of(quote));

        when(account.getId())
                .thenReturn(10L);

        when(
                positionRepository
                        .findByAccountIdAndInstrumentId(
                                10L,
                                1L))
                .thenReturn(
                        Optional.of(position));

        when(position.getQuantity())
                .thenReturn(
                        new BigDecimal("1"));

        OrderValidationService.ValidationResult result = service.validate(
                account,
                instrument,
                OrderSide.SELL,
                new BigDecimal("2"));

        assertFalse(
                result.isAccepted());

        assertEquals(
                "Insufficient holding of this instrument",
                result.getRejectionReason());
    }

    @Test
    void shouldAcceptSellWhenHoldingIsSufficient() {

        when(
                quoteRepository
                        .findTopByInstrumentIdOrderByQuotedAtDesc(
                                1L))
                .thenReturn(
                        Optional.of(quote));

        when(account.getId())
                .thenReturn(10L);

        when(
                positionRepository
                        .findByAccountIdAndInstrumentId(
                                10L,
                                1L))
                .thenReturn(
                        Optional.of(position));

        when(position.getQuantity())
                .thenReturn(
                        new BigDecimal("5"));

        OrderValidationService.ValidationResult result = service.validate(
                account,
                instrument,
                OrderSide.SELL,
                new BigDecimal("2"));

        assertTrue(
                result.isAccepted());

        assertNull(
                result.getRejectionReason());
    }

}