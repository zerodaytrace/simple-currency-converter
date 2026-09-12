package com.zerodaytrace;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * Lightweight, dependency-free test harness (there is no JUnit on the classpath).
 *
 * Compile & run from the repo root:
 *   javac -d out_test src/com/zerodaytrace/*.java test/com/zerodaytrace/*.java
 *   java  -cp out_test com.zerodaytrace.CurrencyTests
 *
 * Exits with status 1 if any assertion fails, so it is CI-friendly.
 */
public class CurrencyTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---- Issue #1: converted amount must not collapse to "0.00" for tiny values ----
        assertEquals("100.00", CurrencyMain.formatAmount(100.0), "formatAmount: normal fiat keeps 2dp");
        assertEquals("1234.50", CurrencyMain.formatAmount(1234.5), "formatAmount: >=0.005 keeps 2dp");
        assertEquals("0.00", CurrencyMain.formatAmount(0.0), "formatAmount: zero stays 0.00");
        assertEquals("0.01", CurrencyMain.formatAmount(0.01), "formatAmount: small-but-visible stays 2dp");
        // These would all be "0.00" under the old %.2f formatting:
        assertNotEquals("0.00", CurrencyMain.formatAmount(0.004), "formatAmount: 0.004 must not vanish");
        assertEquals("0.004", CurrencyMain.formatAmount(0.004), "formatAmount: sub-cent keeps sig figs");
        assertEquals("0.00091", CurrencyMain.formatAmount(0.00091), "formatAmount: tiny value keeps sig figs");
        assertEquals("0.0000091", CurrencyMain.formatAmount(0.0000091), "formatAmount: crypto-scale keeps sig figs");

        // formatRate behaviour is unchanged by the refactor.
        assertEquals("0.9", CurrencyMain.formatRate(0.9), "formatRate: trims trailing zeros");
        assertEquals("147", CurrencyMain.formatRate(147.0), "formatRate: whole number");
        assertEquals("0.0000091", CurrencyMain.formatRate(0.0000091), "formatRate: tiny rate");

        // ---- convert() math, exercised offline via a fake client (no network) ----
        FakeClient fake = new FakeClient(0.9);
        CurrencyConverter converter = new CurrencyConverter(fake);

        CurrencyConverter.ConversionResult usdEur = converter.convert(10, "USD", "EUR");
        assertClose(9.0, usdEur.convertedAmount().doubleValue(), "convert: 10 USD * 0.9 = 9 EUR");
        assertClose(0.9, usdEur.rate(), "convert: rate is surfaced");

        // Same currency short-circuits to rate 1.0 without hitting the API.
        fake.rateCalls = 0;
        CurrencyConverter.ConversionResult same = converter.convert(42, "USD", "usd");
        assertClose(42.0, same.convertedAmount().doubleValue(), "convert: same currency returns amount unchanged");
        assertClose(1.0, same.rate(), "convert: same currency rate is 1.0");
        assertEquals(0, fake.rateCalls, "convert: same currency makes no rate call");

        // End-to-end: a tiny target rate produces a real non-zero amount that formats non-zero.
        CurrencyConverter toBtc = new CurrencyConverter(new FakeClient(0.0000091));
        CurrencyConverter.ConversionResult btc = toBtc.convert(100, "USD", "BTC");
        assertClose(0.00091, btc.convertedAmount().doubleValue(), "convert: 100 USD -> BTC math");
        assertNotEquals("0.00", CurrencyMain.formatAmount(btc.convertedAmount()), "end-to-end: BTC amount is not 0.00");

        // Unsupported currency is rejected.
        assertThrows(IllegalArgumentException.class, () -> converter.convert(1, "USD", "XXX"),
                "convert: unsupported currency throws IllegalArgumentException");

        // ---- Issue #2: money math uses BigDecimal, not a lossy double multiply ----
        CurrencyConverter money = new CurrencyConverter(new FakeClient(1.1));
        assertBigDecimalEquals("1.21", money.convert(1.1, "USD", "EUR").convertedAmount(),
                "convert: 1.1 * 1.1 is exactly 1.21 (a double multiply yields 1.2100000000000002)");
        assertBigDecimalEquals("0.02", new CurrencyConverter(new FakeClient(0.2)).convert(0.1, "USD", "EUR").convertedAmount(),
                "convert: 0.1 * 0.2 is exactly 0.02 (a double multiply yields 0.020000000000000004)");

        // ---- Issue #1: reading input must not throw NoSuchElementException at end-of-input ----
        assertEquals(Optional.empty(), CurrencyMain.nextToken(new Scanner("")),
                "nextToken: end-of-input returns empty instead of throwing");
        assertEquals(Optional.of("100"), CurrencyMain.nextToken(new Scanner("100")),
                "nextToken: returns the next whitespace-delimited token");
        assertEquals(Optional.of("quit"), CurrencyMain.nextToken(new Scanner("quit EUR")),
                "nextToken: passes tokens through (quit is handled by the caller)");

        // ---- Issue #4: the HTTP layer must not be able to hang forever, and must follow redirects ----
        HttpClient client = ExchangeRateApiClient.newHttpClient();
        assertTrue(client.connectTimeout().isPresent(),
                "newHttpClient: has a connect timeout so a stalled TCP handshake can't hang forever");
        assertEquals(HttpClient.Redirect.NORMAL, client.followRedirects(),
                "newHttpClient: follows redirects (the default NEVER treats a 3xx as failure)");
        HttpRequest request = ExchangeRateApiClient.buildRequest("https://example.test/currencies.min.json");
        assertTrue(request.timeout().isPresent(),
                "buildRequest: has a per-request read timeout so a silent server can't hang forever");

        // ---- Issue #7: JSON parsing extracted into pure, network-free seams ----
        String currenciesBody = "{\"eur\":\"Euro\",\"usd\":\"United States Dollar\",\"btc\":\"Bitcoin\"}";
        assertEquals(Set.of("btc", "eur", "usd"),
                ExchangeRateApiClient.parseCurrencyCodes(currenciesBody),
                "parseCurrencyCodes: extracts lowercase codes from the currencies list");
        assertEquals(Set.of(), ExchangeRateApiClient.parseCurrencyCodes("{}"),
                "parseCurrencyCodes: no string-valued keys -> empty set (caller decides it's an error)");

        String ratesBody = "{\"date\":\"2026-09-07\",\"usd\":{\"eur\":0.8613,\"jpy\":156.076,\"sat\":1.2e-5}}";
        assertTrue(ExchangeRateApiClient.parseRate(ratesBody, "eur").isPresent(),
                "parseRate: finds a present target");
        assertClose(0.8613, ExchangeRateApiClient.parseRate(ratesBody, "eur").getAsDouble(),
                "parseRate: parses the numeric rate for the target");
        assertClose(1.2e-5, ExchangeRateApiClient.parseRate(ratesBody, "sat").getAsDouble(),
                "parseRate: parses scientific-notation rates");
        assertTrue(ExchangeRateApiClient.parseRate(ratesBody, "xxx").isEmpty(),
                "parseRate: missing target -> empty (caller turns this into a clear error)");

        // ---- Issue #3: fetch failures must report the real reason, not a blanket message ----
        String fetchMsg = ExchangeRateApiClient.fetchFailureMessage(
                "currencies.min.json",
                java.util.List.of("primary returned HTTP 404", "fallback returned HTTP 500"));
        assertTrue(fetchMsg.contains("404") && fetchMsg.contains("500"),
                "fetchFailureMessage: surfaces each host's real HTTP status");
        assertTrue(fetchMsg.contains("currencies.min.json"),
                "fetchFailureMessage: names the resource that could not be fetched");
        assertTrue(ExchangeRateApiClient.fetchFailureMessage("x", java.util.List.of()).length() > 0,
                "fetchFailureMessage: still returns a message when no per-host detail was captured");

        java.io.IOException fetchCause = new java.io.IOException("connection reset");
        ExchangeRateApiClient.ExchangeRateException chained =
                new ExchangeRateApiClient.ExchangeRateException("wrapped", fetchCause);
        assertEquals(fetchCause, chained.getCause(),
                "ExchangeRateException: preserves the underlying cause for diagnostics");

        // ---- Issue #5: amount parsing must be strict, rejecting forms Double.parseDouble quietly accepts ----
        assertTrue(CurrencyMain.parseStrictAmount("100").isPresent(),
                "parseStrictAmount: plain integer is accepted");
        assertClose(100.0, CurrencyMain.parseStrictAmount("100").getAsDouble(),
                "parseStrictAmount: parses the numeric value");
        assertTrue(CurrencyMain.parseStrictAmount("1234.5").isPresent(),
                "parseStrictAmount: plain decimal is accepted");
        assertTrue(CurrencyMain.parseStrictAmount("0.004").isPresent(),
                "parseStrictAmount: sub-cent decimal is accepted");
        assertTrue(CurrencyMain.parseStrictAmount("-5").isPresent(),
                "parseStrictAmount: a signed number parses (the non-negative check rejects it with a clearer message)");
        assertTrue(CurrencyMain.parseStrictAmount("1.5f").isEmpty(),
                "parseStrictAmount: rejects the float suffix Double.parseDouble accepts as 1.5");
        assertTrue(CurrencyMain.parseStrictAmount("2d").isEmpty(),
                "parseStrictAmount: rejects the double suffix Double.parseDouble accepts as 2");
        assertTrue(CurrencyMain.parseStrictAmount("0x1p4").isEmpty(),
                "parseStrictAmount: rejects the hex float Double.parseDouble accepts as 16");
        assertTrue(CurrencyMain.parseStrictAmount("1e3").isEmpty(),
                "parseStrictAmount: rejects scientific notation for a user-entered amount");
        assertTrue(CurrencyMain.parseStrictAmount("abc").isEmpty(),
                "parseStrictAmount: rejects non-numeric input");
        assertTrue(CurrencyMain.parseStrictAmount("").isEmpty(),
                "parseStrictAmount: rejects empty input");

        // ---- Issue #6: formatting parameters are configurable via MoneyFormatter; DEFAULT is unchanged ----
        assertEquals("100.00", MoneyFormatter.DEFAULT.formatAmount(100.0),
                "MoneyFormatter.DEFAULT: reproduces the existing 2dp amount formatting");
        assertEquals("0.004", MoneyFormatter.DEFAULT.formatAmount(0.004),
                "MoneyFormatter.DEFAULT: reproduces the sub-cent significant-figure fallback");
        assertEquals("0.9", MoneyFormatter.DEFAULT.formatRate(0.9),
                "MoneyFormatter.DEFAULT: reproduces the existing rate formatting");

        MoneyFormatter threeDp = new MoneyFormatter(3, 6, new BigDecimal("0.005"));
        assertEquals("1.500", threeDp.formatAmount(1.5),
                "MoneyFormatter: decimalPlaces is configurable");

        MoneyFormatter threeSig = new MoneyFormatter(2, 3, new BigDecimal("0.005"));
        assertEquals("0.123", threeSig.formatRate(0.123456),
                "MoneyFormatter: significantFigures is configurable");

        MoneyFormatter highThreshold = new MoneyFormatter(2, 6, new BigDecimal("0.5"));
        assertEquals("0.4", highThreshold.formatAmount(0.4),
                "MoneyFormatter: subCentThreshold is configurable (0.4 < 0.5 -> sig-fig path)");
        assertEquals("0.40", MoneyFormatter.DEFAULT.formatAmount(0.4),
                "MoneyFormatter.DEFAULT: 0.4 >= 0.005 keeps 2dp (contrast with the higher threshold)");

        // ---- Issue #8: main() decomposed into cohesive, testable prompt helpers ----
        // (stdout is silenced so the prompts these emit don't pollute the harness output)
        assertClose(100.0, silently(() -> CurrencyMain.promptForAmount(new Scanner("100"))),
                "promptForAmount: returns the parsed amount");
        assertClose(50.0, silently(() -> CurrencyMain.promptForAmount(new Scanner("abc 50"))),
                "promptForAmount: reprompts past non-numeric input");
        assertClose(10.0, silently(() -> CurrencyMain.promptForAmount(new Scanner("-5 10"))),
                "promptForAmount: reprompts past a negative amount");

        CurrencyConverter promptConv = new CurrencyConverter(new FakeClient(1.0));
        assertEquals("eur", silently(() -> CurrencyMain.promptForSupportedCurrency(
                new Scanner("xxx eur"), promptConv, "from: ")),
                "promptForSupportedCurrency: reprompts past an unsupported code, returns a supported one");
        assertEquals("usd", silently(() -> CurrencyMain.promptForSupportedCurrency(
                new Scanner("usd"), promptConv, "from: ")),
                "promptForSupportedCurrency: returns the supported code as entered");

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** Offline stand-in for {@link ExchangeRateApiClient} - never touches the network. */
    static class FakeClient extends ExchangeRateApiClient {
        final double rate;
        int rateCalls = 0;

        FakeClient(double rate) {
            this.rate = rate;
        }

        @Override
        public Set<String> fetchSupportedCurrencies() {
            return Set.of("usd", "eur", "gbp", "jpy", "btc");
        }

        @Override
        public double fetchRate(String from, String to) {
            rateCalls++;
            return rate;
        }
    }

    // ---- minimal assertion helpers ----
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(msg);
        } else {
            fail(msg + " (expected <" + expected + ">, got <" + actual + ">)");
        }
    }

    private static void assertNotEquals(Object unexpected, Object actual, String msg) {
        if (unexpected == null ? actual != null : !unexpected.equals(actual)) {
            pass(msg);
        } else {
            fail(msg + " (did not expect <" + unexpected + ">)");
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (condition) {
            pass(msg);
        } else {
            fail(msg + " (expected true)");
        }
    }

    private static void assertClose(double expected, double actual, String msg) {
        if (Math.abs(expected - actual) < 1e-9) {
            pass(msg);
        } else {
            fail(msg + " (expected <" + expected + ">, got <" + actual + ">)");
        }
    }

    private static void assertBigDecimalEquals(String expected, BigDecimal actual, String msg) {
        if (actual != null && new BigDecimal(expected).compareTo(actual) == 0) {
            pass(msg);
        } else {
            fail(msg + " (expected <" + expected + ">, got <" + actual + ">)");
        }
    }

    private static void assertThrows(Class<? extends Throwable> type, Runnable action, String msg) {
        try {
            action.run();
            fail(msg + " (no exception thrown)");
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                pass(msg);
            } else {
                fail(msg + " (expected " + type.getSimpleName() + ", got " + t.getClass().getSimpleName() + ")");
            }
        }
    }

    // Runs an action with stdout redirected to a throwaway buffer, so interactive prompts
    // emitted by the code under test don't interleave with the PASS/FAIL lines. Restores
    // System.out before returning, so the subsequent assertion prints normally.
    private static <T> T silently(java.util.function.Supplier<T> action) {
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        try {
            return action.get();
        } finally {
            System.setOut(original);
        }
    }

    private static void pass(String msg) {
        passed++;
        System.out.println("PASS: " + msg);
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("FAIL: " + msg);
    }
}
