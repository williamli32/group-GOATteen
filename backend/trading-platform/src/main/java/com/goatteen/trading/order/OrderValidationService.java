package com.goatteen.trading.order;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
public class OrderValidationService {

    private final PositionRepository positionRepository;

    public OrderValidationService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    public ValidationResult validate(Account account, Instrument instrument, OrderSide side, BigDecimal quantity) {
        if (!instrument.isTradable()) {
            return ValidationResult.rejected("Instrument is not currently tradable");
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.rejected("Quantity must be greater than zero");
        }

        if (side == OrderSide.BUY) {
            return validateBuyOrder(account, instrument, quantity);
        } else {
            return validateSellOrder(account, instrument, quantity);
        }
    }

    private ValidationResult validateBuyOrder(Account account, Instrument instrument, BigDecimal quantity) {
        // For now, assume 1:1 pricing (no FX conversion yet per BR-12 gap)
        // In production: fetch current quote and convert to account currency
        if (account.getCashBalance().compareTo(quantity) < 0) {
            return ValidationResult.rejected("Insufficient cash balance");
        }
        return ValidationResult.accepted();
    }

    private ValidationResult validateSellOrder(Account account, Instrument instrument, BigDecimal quantity) {
        Position position = positionRepository
                .findByAccountIdAndInstrumentId(account.getId(), instrument.getId())
                .orElse(null);

        if (position == null || position.getQuantity().compareTo(quantity) < 0) {
            return ValidationResult.rejected("Insufficient holding of this instrument");
        }

        return ValidationResult.accepted();
    }

    public static class ValidationResult {
        private final boolean accepted;
        private final String rejectionReason;

        private ValidationResult(boolean accepted, String rejectionReason) {
            this.accepted = accepted;
            this.rejectionReason = rejectionReason;
        }

        public static ValidationResult accepted() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }
}
