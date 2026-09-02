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
            System.out.println("Unsupported currency. Only accepted currency codes are allowed: "
                    + converter.getSupportedCurrencies());
        }

        String toCurrency;
        while (true) {
            System.out.print("Enter currency to convert to (or 'quit' or 'q' to exit): ");
            toCurrency = scanner.next();

            exitIfQuit(toCurrency, scanner);
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

    private static void exitIfQuit(String input, Scanner scanner) {
        if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
            System.out.println("Terminating program...done");
            scanner.close();
            System.exit(0);
        }
    }
}
