package com.zerodaytrace;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Scanner;
import java.util.regex.Pattern;

public class CurrencyMain {
    public static void main(String[] args) {
        var converter = new CurrencyConverter();
        Scanner scanner = new Scanner(System.in);

        System.out.println("\nWelcome to the Currency Converter Program!\n");

        if (!printSupportedCurrencyCount(converter, scanner)) {
            return;
        }

        double amount = promptForAmount(scanner);
        String fromCurrency = promptForSupportedCurrency(scanner, converter,
                "Enter currency to convert from (or 'quit' or 'q' to exit): ");
        String toCurrency = promptForSupportedCurrency(scanner, converter,
                "Enter currency to convert to (or 'quit' or 'q' to exit): ");

        scanner.close();

        printConversion(converter, amount, fromCurrency, toCurrency);
    }

    // Issue #8: each step of the old monolithic main() is now a single-purpose method, so
    // main() reads as a high-level script and the prompt loops become independently testable.

    // Reports how many currencies are available, or that the service is unreachable. Returns
    // false (after closing the scanner) when the app should stop before prompting the user.
    private static boolean printSupportedCurrencyCount(CurrencyConverter converter, Scanner scanner) {
        try {
            int count = converter.getSupportedCurrencies().size();
            System.out.println(count + " currencies available for live exchange rate (e.g. USD, EUR, GBP, JPY, BTC).");
            return true;
        } catch (ExchangeRateApiClient.ExchangeRateException e) {
            System.out.println("\nUnable to reach the exchange rate service. "
                    + "Please check your connection and try again.");
            scanner.close();
            return false;
        }
    }

    // Prompts until a well-formed, non-negative, finite amount is entered (quit / end-of-input
    // are handled by readTokenOrExit). Package-private so the reprompt logic can be unit-tested.
    static double promptForAmount(Scanner scanner) {
        while (true) {
            System.out.print("Enter amount to convert (or 'quit' or 'q' to exit): ");
            String input = readTokenOrExit(scanner);

            OptionalDouble parsed = parseStrictAmount(input);
            if (parsed.isEmpty()) {
                System.out.println("Invalid amount. Please enter a numeric value.");
                continue;
            }
            double value = parsed.getAsDouble();
            if (!Double.isFinite(value) || value < 0) {
                System.out.println("Please enter a non-negative, finite amount.");
                continue;
            }
            return value;
        }
    }

    // Prompts with the given message until a supported currency code is entered, returning it
    // as typed. Package-private so it can be unit-tested with an offline converter.
    static String promptForSupportedCurrency(Scanner scanner, CurrencyConverter converter, String prompt) {
        while (true) {
            System.out.print(prompt);
            String code = readTokenOrExit(scanner);
            if (converter.isSupported(code)) {
                return code;
            }
            System.out.println("Unsupported currency code. Please enter a valid code (e.g. USD, EUR, GBP).");
        }
    }

    // Performs the conversion and prints the result, or prints the error message on failure.
    private static void printConversion(CurrencyConverter converter, double amount,
                                        String fromCurrency, String toCurrency) {
        try {
            var result = converter.convert(amount, fromCurrency, toCurrency);
            String from = fromCurrency.toUpperCase(Locale.ROOT);
            String to = toCurrency.toUpperCase(Locale.ROOT);

            System.out.printf(Locale.ROOT, "%s %s = %s %s%n", formatAmount(amount), from, formatAmount(result.convertedAmount()), to);
            System.out.printf(Locale.ROOT, "Rate: 1 %s = %s %s%n", from, formatRate(result.rate()), to);
        } catch (IllegalArgumentException | ExchangeRateApiClient.ExchangeRateException e) {
            System.out.println(e.getMessage());
        }
    }

    // Scanner.next() throws NoSuchElementException at end-of-input (piped input, a closed
    // stream, Ctrl+Z on Windows). Guarding with hasNext() turns EOF into a clean signal
    // (empty) that the caller translates into a graceful exit instead of a stack trace.
    static Optional<String> nextToken(Scanner scanner) {
        return scanner.hasNext() ? Optional.of(scanner.next()) : Optional.empty();
    }

    // Reads a token, exiting gracefully on end-of-input, then applies the existing
    // 'quit'/'q' handling. Callers always receive a real, non-terminating token.
    private static String readTokenOrExit(Scanner scanner) {
        Optional<String> token = nextToken(scanner);
        if (token.isEmpty()) {
            System.out.println("\nNo more input. Terminating program...done");
            scanner.close();
            System.exit(0);
        }
        String value = token.get();
        exitIfQuit(value, scanner);
        return value;
    }

    private static void exitIfQuit(String input, Scanner scanner) {
        if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
            System.out.println("Terminating program...done");
            scanner.close();
            System.exit(0);
        }
    }

    // Issue #5: Double.parseDouble is too lenient for user input - it silently accepts
    // "100f"/"2d" suffixes, hex floats like "0x1p4" (=16) and scientific notation. A typed
    // amount should be a plain decimal, so we gate on this pattern before parsing.
    private static final Pattern STRICT_AMOUNT = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    // Pure/testable seam: returns the value only for a well-formed plain decimal; the
    // finite/non-negative range check stays with the caller so it can give a tailored message.
    static OptionalDouble parseStrictAmount(String input) {
        if (input == null || !STRICT_AMOUNT.matcher(input).matches()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(input));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    // Issue #6: the formatting parameters now live in MoneyFormatter. These static helpers
    // keep the existing call sites and tests working by delegating to the default config.
    static String formatAmount(double value) {
        return MoneyFormatter.DEFAULT.formatAmount(value);
    }

    static String formatAmount(BigDecimal value) {
        return MoneyFormatter.DEFAULT.formatAmount(value);
    }

    static String formatRate(double rate) {
        return MoneyFormatter.DEFAULT.formatRate(rate);
    }
}
