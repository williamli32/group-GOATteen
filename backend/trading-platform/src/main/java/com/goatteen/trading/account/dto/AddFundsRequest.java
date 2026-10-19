package com.goatteen.trading.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AddFundsRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotNull UUID idempotencyKey) {
}
