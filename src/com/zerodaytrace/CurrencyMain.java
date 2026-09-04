package com.zerodaytrace;

import java.util.Locale;
import java.util.Scanner;

public class CurrencyMain {
    public static void main(String[] args) {
        var converter = new CurrencyConverter();
        Scanner scanner = new Scanner(System.in);

        System.out.println("\nWelcome to the Currency Converter Program!\n");

        try {
            int count = converter.getSupportedCurrencies().size();
            System.out.println(count + " currencies available for live exchange rate (e.g. USD, EUR, GBP, JPY, BTC).");
        } catch (ExchangeRateApiClient.ExchangeRateException e) {
            System.out.println("\nUnable to reach the exchange rate service. "
                    + "Please check your connection and try again.");
            scanner.close();
            return;
        }

        double amount = 0;
        while (true) {
            System.out.print("Enter amount to convert (or 'quit' or 'q' to exit): ");
            String input = scanner.next();
            
            exitIfQuit(input, scanner);

            try {
                amount = Double.parseDouble(input);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount. Please enter a numeric value.");
            }
        }

        String fromCurrency;
        while (true) {
            System.out.print("Enter currency to convert from (or 'quit' or 'q' to exit): ");
            fromCurrency = scanner.next();

            exitIfQuit(fromCurrency, scanner);

            if (converter.isSupported(fromCurrency)) {
                break;
            }
            System.out.println("Unsupported currency code. Please enter a valid code (e.g. USD, EUR, GBP).");
        }

        String toCurrency;
        while (true) {
            System.out.print("Enter currency to convert to (or 'quit' or 'q' to exit): ");
            toCurrency = scanner.next();

            exitIfQuit(toCurrency, scanner);
            
            if (converter.isSupported(toCurrency)) {
                break;
            }
            System.out.println("Unsupported currency code. Please enter a valid code (e.g. USD, EUR, GBP).");
        }

        scanner.close();

        try {
            double result = converter.convert(amount, fromCurrency, toCurrency);

            System.out.printf("%.2f %s = %.2f %s", amount, 
            fromCurrency.toUpperCase(Locale.ROOT), result, 
            toCurrency.toUpperCase(Locale.ROOT));  
            
        } catch (IllegalArgumentException | ExchangeRateApiClient.ExchangeRateException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void exitIfQuit(String input, Scanner scanner) {
        if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
            System.out.println("Terminating program...done");
            scanner.close();
            System.exit(0);
        }
    }
}
