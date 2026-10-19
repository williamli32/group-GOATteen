package com.goatteen.trading.order;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.fx.FxRateService;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.marketdata.QuoteRepository;
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderValidationService {

        private final PositionRepository positionRepository;
        private final QuoteRepository quoteRepository;
        private final FxRateService fxRateService;

        public OrderValidationService(
                        PositionRepository positionRepository,
                        QuoteRepository quoteRepository,
                        FxRateService fxRateService) {
                this.positionRepository = positionRepository;

                this.quoteRepository = quoteRepository;
                this.fxRateService = fxRateService;
        }

        public ValidationResult validate(
                        Account account,
                        Instrument instrument,
                        OrderSide side,
                        BigDecimal quantity) {

                if (!instrument.isTradable()) {

                        return ValidationResult.rejected(
                                        "Instrument is not currently tradable");
                }

                if (quantity == null ||
                                quantity.compareTo(
                                                BigDecimal.ZERO) <= 0) {

                        return ValidationResult.rejected(
                                        "Quantity must be greater than zero");
                }

                if (side == null) {

                        return ValidationResult.rejected(
                                        "Order side is required");
                }

                Quote quote = quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(
                                                instrument.getId())
                                .orElse(null);

                if (quote == null) {

                        return ValidationResult.rejected(
                                        "No market quote available for instrument");
                }

                if (side == OrderSide.BUY) {

                        return validateBuyOrder(
                                        account,
                                        instrument,
                                        quantity,
                                        quote);
                }

                return validateSellOrder(
                                account,
                                instrument,
                                quantity);
        }

        private ValidationResult validateBuyOrder(
                        Account account,
                        Instrument instrument,
                        BigDecimal quantity,
                        Quote quote) {
                try {
                        // The ask is expressed in instrument currency; compare account currency.
                        var fx = fxRateService.quote(instrument.getCurrency(), account.getCurrency());
                        BigDecimal estimatedCost = fxRateService.settlementAmount(
                                        quote.getAskPrice().multiply(quantity), fx, OrderSide.BUY);
                        if (account.getCashBalance().compareTo(estimatedCost) < 0) {
                                return ValidationResult.rejected("Insufficient cash balance");
                        }
                        return ValidationResult.accepted();
                } catch (FxRateService.FxRateUnavailableException ex) {
                        return ValidationResult.rejected(ex.getMessage());
                }
        }

        private ValidationResult validateSellOrder(
                        Account account,
                        Instrument instrument,
                        BigDecimal quantity) {
                try {
                        fxRateService.quote(instrument.getCurrency(), account.getCurrency());
                } catch (FxRateService.FxRateUnavailableException ex) {
                        return ValidationResult.rejected(ex.getMessage());
                }

                Position position = positionRepository
                                .findByAccountIdAndInstrumentId(
                                                account.getId(),
                                                instrument.getId())
                                .orElse(null);

                if (position == null ||
                                position.getQuantity()
                                                .compareTo(
                                                                quantity) < 0) {

                        return ValidationResult.rejected(
                                        "Insufficient holding of this instrument");
                }

                return ValidationResult.accepted();
        }

        public static class ValidationResult {

                private final boolean accepted;

                private final String rejectionReason;

                private ValidationResult(
                                boolean accepted,
                                String rejectionReason) {
                        this.accepted = accepted;
                        this.rejectionReason = rejectionReason;
                }

                public static ValidationResult accepted() {

                        return new ValidationResult(
                                        true,
                                        null);
                }

                public static ValidationResult rejected(
                                String reason) {

                        return new ValidationResult(
                                        false,
                                        reason);
                }

                public boolean isAccepted() {
                        return accepted;
                }

                public String getRejectionReason() {
                        return rejectionReason;
                }
        }
}