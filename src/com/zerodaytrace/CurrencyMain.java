package com.zerodaytrace;

import java.util.Locale;
import java.util.Scanner;

public class CurrencyMain {
    public static void main(String[] args) {
        var converter = new CurrencyConverter();
        Scanner scanner = new Scanner(System.in);

        System.out.println("\nWelcome to the Currency Converter Program!");
        System.out.println("\nSupported currencies: " + converter.getSupportedCurrencies());

        double amount = 0;
        while (true) {
            System.out.print("Enter amount to convert: ");
            if (scanner.hasNextDouble()) {
                amount = scanner.nextDouble();
                break;
            }
            System.out.println("Invalid amount. Please enter a numeric value.");
            scanner.next(); 
        }

        String fromCurrency;
        while (true) {
            System.out.print("Enter currency to convert from: ");
            fromCurrency = scanner.next();
            if (converter.isSupported(fromCurrency)) {
                break;
            }
            System.out.println("Unsupported currency. Only accepted currency codes are allowed: "
                    + converter.getSupportedCurrencies());
        }

        String toCurrency;
        while (true) {
            System.out.print("Enter currency to convert to: ");
            toCurrency = scanner.next();
            if (converter.isSupported(toCurrency)) {
                break;
            }
            System.out.println("Unsupported currency. Only accepted currency codes are allowed: "
                    + converter.getSupportedCurrencies());
        }

        scanner.close();

        try {
            double result = converter.convert(amount, fromCurrency, toCurrency);

            System.out.printf("%.2f %s = %.2f %s", amount, 
            fromCurrency.toUpperCase(Locale.ROOT), result, 
            toCurrency.toUpperCase(Locale.ROOT));
            
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }
}
