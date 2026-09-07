package com.zerodaytrace;

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
        assertClose(9.0, usdEur.convertedAmount(), "convert: 10 USD * 0.9 = 9 EUR");
        assertClose(0.9, usdEur.rate(), "convert: rate is surfaced");

        // Same currency short-circuits to rate 1.0 without hitting the API.
        fake.rateCalls = 0;
        CurrencyConverter.ConversionResult same = converter.convert(42, "USD", "usd");
        assertClose(42.0, same.convertedAmount(), "convert: same currency returns amount unchanged");
        assertClose(1.0, same.rate(), "convert: same currency rate is 1.0");
        assertEquals(0, fake.rateCalls, "convert: same currency makes no rate call");

        // End-to-end: a tiny target rate produces a real non-zero amount that formats non-zero.
        CurrencyConverter toBtc = new CurrencyConverter(new FakeClient(0.0000091));
        CurrencyConverter.ConversionResult btc = toBtc.convert(100, "USD", "BTC");
        assertClose(0.00091, btc.convertedAmount(), "convert: 100 USD -> BTC math");
        assertNotEquals("0.00", CurrencyMain.formatAmount(btc.convertedAmount()), "end-to-end: BTC amount is not 0.00");

        // Unsupported currency is rejected.
        assertThrows(IllegalArgumentException.class, () -> converter.convert(1, "USD", "XXX"),
                "convert: unsupported currency throws IllegalArgumentException");

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

    private static void assertClose(double expected, double actual, String msg) {
        if (Math.abs(expected - actual) < 1e-9) {
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

    private static void pass(String msg) {
        passed++;
        System.out.println("PASS: " + msg);
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("FAIL: " + msg);
    }
}
