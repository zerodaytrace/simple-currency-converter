package com.zerodaytrace;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;


public class CurrencyConverter {
    private final ExchangeRateApiClient api;
    private Set<String> supportedCurrencies;

    public CurrencyConverter() {
        this(new ExchangeRateApiClient());
    }

   
    public CurrencyConverter(ExchangeRateApiClient api) {
        this.api = api;
    }

   
    public Set<String> getSupportedCurrencies() {
        if (supportedCurrencies == null) {
            supportedCurrencies = api.fetchSupportedCurrencies();
        }
        return supportedCurrencies;
    }

    public boolean isSupported(String currencyCode) {
        return getSupportedCurrencies().contains(currencyCode.toLowerCase(Locale.ROOT));
    }

    public ConversionResult convert(double amount, String fromCurrency, String toCurrency) {
        String from = fromCurrency.toLowerCase(Locale.ROOT);
        String to = toCurrency.toLowerCase(Locale.ROOT);

        if (!isSupported(from) || !isSupported(to)) {
            throw new IllegalArgumentException("Unsupported currency");
        }
        if (from.equals(to)) {
            return new ConversionResult(BigDecimal.valueOf(amount), 1.0);
        }
        double rate = api.fetchRate(from, to);
        // BigDecimal.valueOf uses the canonical shortest decimal (so 0.1 stays 0.1, not the
        // new BigDecimal(0.1) = 0.1000...0055 trap), and multiplying in BigDecimal avoids the
        // double-rounding of `amount * rate`.
        return new ConversionResult(BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(rate)), rate);
    }

    // Issue #2: the converted amount is BigDecimal so precision survives for downstream
    // use (summing, large amounts, A->B->A). The rate stays double: it arrives as one.
    public record ConversionResult(BigDecimal convertedAmount, double rate) { }
}
