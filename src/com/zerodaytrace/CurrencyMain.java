package com.zerodaytrace;

import java.util.Locale;
import java.util.Scanner;

public class CurrencyMain {
    public static void main(String[] args) {
        var converter = new CurrencyConverter();
        Scanner scanner = new Scanner(System.in);

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

        System.out.print("Enter currency to convert from: ");
        String fromCurrency = scanner.next();

        System.out.print("Enter currency to convert to: ");
        String toCurrency = scanner.next();

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
