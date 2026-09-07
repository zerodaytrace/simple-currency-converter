package com.zerodaytrace;

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
            return new ConversionResult(amount, 1.0);
        }
        double rate = api.fetchRate(from, to);
        return new ConversionResult(amount * rate, rate);
    }

    public record ConversionResult(double convertedAmount, double rate) { }
}
