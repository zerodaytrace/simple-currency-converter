package com.zerodaytrace;

import java.util.*;

public class CurrencyConverter {
    private final Map<String, Double> ratesToUSD = new HashMap<>();

    public CurrencyConverter() {
        ratesToUSD.put("USD", 1.00);
        ratesToUSD.put("EUR", 1.05);
        ratesToUSD.put("GBP", 1.37);
        ratesToUSD.put("CAD", 0.89);
        ratesToUSD.put("AUD", 0.79);
        ratesToUSD.put("JPY", 0.0097);
    }

    public Set<String> getSupportedCurrencies() {
        return new TreeSet<>(ratesToUSD.keySet());
    }

    public double convert(double amount, String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase(Locale.ROOT);
        String to = toCurrency.toUpperCase(Locale.ROOT);

        if (!ratesToUSD.containsKey(from) || !ratesToUSD.containsKey(to)) 
            throw new IllegalArgumentException("Unsupported currency");
        

        double amountInUSD = amount * ratesToUSD.get(from);
        double convertedAmount = amountInUSD / ratesToUSD.get(to);

        return convertedAmount;
    }


}