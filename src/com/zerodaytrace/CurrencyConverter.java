package com.zerodaytrace;

import java.util.Locale;
import java.util.Set;

/**
 * Converts between currencies using live exchange rates supplied by an
 * {@link ExchangeRateApiClient}. The list of supported currencies is fetched
 * once on first use and cached for the lifetime of this instance.
 */
public class CurrencyConverter {
    private final ExchangeRateApiClient api;
    private Set<String> supportedCurrencies;

    public CurrencyConverter() {
        this(new ExchangeRateApiClient());
    }

    // Allows a different (e.g. test) client to be supplied.
    public CurrencyConverter(ExchangeRateApiClient api) {
        this.api = api;
    }

    /** Lower-case codes of every currency the API supports (fetched once, then cached). */
    public Set<String> getSupportedCurrencies() {
        if (supportedCurrencies == null) {
            supportedCurrencies = api.fetchSupportedCurrencies();
        }
        return supportedCurrencies;
    }

    public boolean isSupported(String currencyCode) {
        return getSupportedCurrencies().contains(currencyCode.toLowerCase(Locale.ROOT));
    }

    public double convert(double amount, String fromCurrency, String toCurrency) {
        String from = fromCurrency.toLowerCase(Locale.ROOT);
        String to = toCurrency.toLowerCase(Locale.ROOT);

        if (!isSupported(from) || !isSupported(to)) {
            throw new IllegalArgumentException("Unsupported currency");
        }
        if (from.equals(to)) {
            return amount;
        }
        return amount * api.fetchRate(from, to);
    }
}
