package com.pfe.marketplace.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/** Formats amounts in customer-facing messages, e.g. "€12.50". */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    public static String symbol(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getSymbol(Locale.FRANCE);
        } catch (IllegalArgumentException e) {
            return currencyCode.toUpperCase(Locale.ROOT) + " ";
        }
    }

    public static String format(String currencyCode, BigDecimal amount) {
        return symbol(currencyCode) + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
