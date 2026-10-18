package com.goatteen.trading.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public enum AccountCurrency {
    GBP, USD, EUR, INR, JPY, CHF, AUD, CAD, NZD, SGD;

    public static List<AccountCurrency> registrationCurrencies() {
        return List.of(USD, GBP, INR);
    }

    public static AccountCurrency forRegistration(String value) {
        AccountCurrency currency = fromCode(value);
        if (!registrationCurrencies().contains(currency)) {
            throw new IllegalArgumentException("Select USD, GBP or INR as your account currency");
        }
        return currency;
    }

    // Existing accounts retain their original currency and amount precision.
    public static AccountCurrency fromCode(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Select a supported account currency");
        }
    }

    public String displayName() {
        return Currency.getInstance(name()).getDisplayName(Locale.ENGLISH);
    }

    public int minorUnits() {
        return Currency.getInstance(name()).getDefaultFractionDigits();
    }

    public BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        try {
            BigDecimal normalized = amount.setScale(minorUnits(), RoundingMode.UNNECESSARY);
            if (normalized.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
                throw new IllegalArgumentException("Amount exceeds the account balance limit");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name() + " amounts support up to " + minorUnits() + " decimal places");
        }
    }
}
