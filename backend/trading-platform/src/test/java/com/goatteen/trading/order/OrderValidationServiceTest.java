
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
                                quoteRepository,
                                new com.goatteen.trading.fx.FxRateService(
                                                org.springframework.web.client.RestClient.builder()));

                when(instrument.getId())
                                .thenReturn(1L);

                when(instrument.isTradable())
                                .thenReturn(true);
        }

        @Test
        void shouldRejectBuyWhenQuoteIsMissing() {

                when(quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                                .thenReturn(Optional.empty());

                OrderValidationService.ValidationResult result = service.validate(
                                account,
                                instrument,
                                OrderSide.BUY,
                                BigDecimal.ONE);

                assertFalse(result.isAccepted());

                assertEquals(
                                "No market quote available for instrument",
                                result.getRejectionReason());
        }

        @Test
        void shouldAcceptCrossCurrencyBuyWhenConvertedCostIsAffordable() {
                var fx = org.mockito.Mockito.mock(com.goatteen.trading.fx.FxRateService.class);
                var snapshot = new com.goatteen.trading.fx.FxRateService.FxQuote(
                                "USD", "GBP", new BigDecimal("0.7502"),
                                java.time.LocalDate.of(2026, 10, 19), "TEST");
                service = new OrderValidationService(positionRepository, quoteRepository, fx);
                when(quoteRepository.findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                                .thenReturn(Optional.of(quote));
                when(account.getCurrency()).thenReturn("GBP");
                when(instrument.getCurrency()).thenReturn("USD");
                when(quote.getAskPrice()).thenReturn(new BigDecimal("100.00"));
                when(account.getCashBalance()).thenReturn(new BigDecimal("100.00"));
                when(fx.quote("USD", "GBP")).thenReturn(snapshot);
                when(fx.settlementAmount(new BigDecimal("100.00"), snapshot, OrderSide.BUY))
                                .thenReturn(new BigDecimal("75.02"));
                var result = service.validate(account, instrument, OrderSide.BUY, BigDecimal.ONE);
                assertTrue(result.isAccepted());
        }

        @Test
        void shouldRejectBuyWhenCashIsInsufficient() {

                when(quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                                .thenReturn(Optional.of(quote));

                when(account.getCurrency())
                                .thenReturn("GBP");

                when(instrument.getCurrency())
                                .thenReturn("GBP");

                when(account.getCashBalance())
                                .thenReturn(new BigDecimal("100.00"));

                when(quote.getAskPrice())
                                .thenReturn(new BigDecimal("75.10"));

                OrderValidationService.ValidationResult result = service.validate(
                                account,
                                instrument,
                                OrderSide.BUY,
                                new BigDecimal("2"));

                assertFalse(result.isAccepted());

                assertEquals(
                                "Insufficient cash balance",
                                result.getRejectionReason());
        }

        @Test
        void shouldAcceptBuyWhenCashCoversAskPriceTimesQuantity() {

                when(quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                                .thenReturn(Optional.of(quote));

                when(account.getCurrency())
                                .thenReturn("GBP");

                when(instrument.getCurrency())
                                .thenReturn("GBP");

                when(account.getCashBalance())
                                .thenReturn(new BigDecimal("1000.00"));

                when(quote.getAskPrice())
                                .thenReturn(new BigDecimal("75.10"));

                OrderValidationService.ValidationResult result = service.validate(
                                account,
                                instrument,
                                OrderSide.BUY,
                                new BigDecimal("2"));

                assertTrue(result.isAccepted());

                assertNull(result.getRejectionReason());
        }

        @Test
        void shouldRejectSellWhenHoldingIsInsufficient() {

                when(quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                                .thenReturn(Optional.of(quote));

                // The currencies must match before holdings are checked.
                when(account.getCurrency())
                                .thenReturn("GBP");

                when(instrument.getCurrency())
                                .thenReturn("GBP");

                when(account.getId())
                                .thenReturn(10L);

                when(positionRepository
                                .findByAccountIdAndInstrumentId(10L, 1L))
                                .thenReturn(Optional.of(position));

                when(position.getQuantity())
                                .thenReturn(new BigDecimal("1"));

                OrderValidationService.ValidationResult result = service.validate(
                                account,
                                instrument,
                                OrderSide.SELL,
                                new BigDecimal("2"));

                assertFalse(result.isAccepted());

                assertEquals(
                                "Insufficient holding of this instrument",
                                result.getRejectionReason());
        }

        @Test
        void shouldAcceptSellWhenHoldingIsSufficient() {

                when(quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                                .thenReturn(Optional.of(quote));

                // The currencies must match before holdings are checked.
                when(account.getCurrency())
                                .thenReturn("GBP");

                when(instrument.getCurrency())
                                .thenReturn("GBP");

                when(account.getId())
                                .thenReturn(10L);

                when(positionRepository
                                .findByAccountIdAndInstrumentId(10L, 1L))
                                .thenReturn(Optional.of(position));

                when(position.getQuantity())
                                .thenReturn(new BigDecimal("5"));

                OrderValidationService.ValidationResult result = service.validate(
                                account,
                                instrument,
                                OrderSide.SELL,
                                new BigDecimal("2"));

                assertTrue(result.isAccepted());

                assertNull(result.getRejectionReason());
        }
}
