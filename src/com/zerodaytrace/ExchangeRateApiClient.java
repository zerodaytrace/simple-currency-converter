package com.zerodaytrace;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the fawazahmed0/exchange-api to fetch the list of supported
 * currencies and individual exchange rates.
 *
 * Zero external dependencies: uses the JDK's built-in {@link HttpClient} and
 * light-weight regex parsing of the (minified) JSON responses.
 */
public class ExchangeRateApiClient {

    private static final String DEFAULT_PRIMARY =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/";
    private static final String DEFAULT_FALLBACK =
            "https://latest.currency-api.pages.dev/v1/";

    // Matches a JSON key whose value is a string: "code":"Name" -> captures the code.
    private static final Pattern CODE_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"");

    private final HttpClient http = HttpClient.newHttpClient();
    private final String primaryHost;
    private final String fallbackHost;

    public ExchangeRateApiClient() {
        this(DEFAULT_PRIMARY, DEFAULT_FALLBACK);
    }

    // Allows alternative hosts to be supplied (useful for testing).
    public ExchangeRateApiClient(String primaryHost, String fallbackHost) {
        this.primaryHost = primaryHost;
        this.fallbackHost = fallbackHost;
    }

    /** Returns the set of supported (lower-case) currency codes. */
    public Set<String> fetchSupportedCurrencies() {
        String body = fetch("currencies.min.json");
        Set<String> codes = new TreeSet<>();
        Matcher matcher = CODE_PATTERN.matcher(body);
        while (matcher.find()) {
            codes.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (codes.isEmpty()) {
            throw new ExchangeRateException("Could not read the list of currencies.");
        }
        return codes;
    }

    /** Returns the exchange rate used to convert {@code from} into {@code to}. */
    public double fetchRate(String from, String to) {
        String base = from.toLowerCase(Locale.ROOT);
        String target = to.toLowerCase(Locale.ROOT);
        String body = fetch("currencies/" + base + ".min.json");

        // Within the base object every value is numeric, so anchoring on a
        // numeric value skips the "date" string and the base key's object.
        Pattern ratePattern = Pattern.compile(
                "\"" + Pattern.quote(target) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
        Matcher matcher = ratePattern.matcher(body);
        if (!matcher.find()) {
            throw new ExchangeRateException("No exchange rate available for "
                    + from.toUpperCase(Locale.ROOT) + " -> " + to.toUpperCase(Locale.ROOT) + ".");
        }
        return Double.parseDouble(matcher.group(1));
    }

    /** GETs {@code path} from the primary host, falling back to the mirror host. */
    private String fetch(String path) {
        for (String host : new String[]{primaryHost, fallbackHost}) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(host + path)).GET().build();
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // don't keep retrying if the thread was interrupted
            } catch (IOException e) {
                // network problem with this host; fall through and try the next one
            }
        }
        throw new ExchangeRateException("Unable to reach the exchange rate service.");
    }

    /** Thrown when the API cannot be reached or returns data we cannot use. */
    public static class ExchangeRateException extends RuntimeException {
        public ExchangeRateException(String message) {
            super(message);
        }
    }
}
