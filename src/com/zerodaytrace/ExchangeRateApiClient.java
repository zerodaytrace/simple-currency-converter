package com.zerodaytrace;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
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

    // Bounds both phases of a request so a stalled network can never hang the app:
    // connectTimeout covers the TCP/TLS handshake, the per-request timeout covers a
    // server that accepts the connection but never sends the body.
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    
    // Matches a JSON key whose value is a string: "code":"Name" -> captures the code.
    private static final Pattern CODE_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"");

    private final HttpClient http = newHttpClient();
    private final String primaryHost;
    private final String fallbackHost;

    public ExchangeRateApiClient() {
        this(DEFAULT_PRIMARY, DEFAULT_FALLBACK);
    }

    
    public ExchangeRateApiClient(String primaryHost, String fallbackHost) {
        this.primaryHost = primaryHost;
        this.fallbackHost = fallbackHost;
    }

    
    public Set<String> fetchSupportedCurrencies() {
        Set<String> codes = parseCurrencyCodes(fetch("currencies.min.json"));
        if (codes.isEmpty()) {
            throw new ExchangeRateException("Could not read the list of currencies.");
        }
        return codes;
    }

    
    public double fetchRate(String from, String to) {
        String base = from.toLowerCase(Locale.ROOT);
        String target = to.toLowerCase(Locale.ROOT);
        String body = fetch("currencies/" + base + ".min.json");
        return parseRate(body, target).orElseThrow(() ->
                new ExchangeRateException("No exchange rate available for "
                        + from.toUpperCase(Locale.ROOT) + " -> " + to.toUpperCase(Locale.ROOT) + "."));
    }

    
    // Issue #7: parsing lifted out of the network methods into pure, testable seams.
    // Matches "code":"Name" pairs; the CDN's flat map has only string values here, so
    // every match is a real currency code.
    static Set<String> parseCurrencyCodes(String body) {
        Set<String> codes = new TreeSet<>();
        Matcher matcher = CODE_PATTERN.matcher(body);
        while (matcher.find()) {
            codes.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return codes;
    }

    // Within the base object every value is numeric, so anchoring on a numeric value
    // skips the "date" string and the base key's nested object. Empty = target absent.
    static OptionalDouble parseRate(String body, String target) {
        Pattern ratePattern = Pattern.compile(
                "\"" + Pattern.quote(target) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
        Matcher matcher = ratePattern.matcher(body);
        if (matcher.find()) {
            return OptionalDouble.of(Double.parseDouble(matcher.group(1)));
        }
        return OptionalDouble.empty();
    }

    // followRedirects(NORMAL) matters because the primary URL pins "@latest"; if the CDN
    // answers with a 3xx, the JDK default (NEVER) would surface it as a non-200 failure.
    static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    static HttpRequest buildRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
    }

    private String fetch(String path) {
        // Issue #3: collect *why* each host failed (bad status vs. connection error) so the
        // thrown exception can report the real reason instead of a blanket "unable to reach".
        List<String> failures = new ArrayList<>();
        IOException lastCause = null;
        for (String host : new String[]{primaryHost, fallbackHost}) {
            try {
                HttpRequest request = buildRequest(host + path);
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                // Reached the host but it answered non-200: a service problem, not the user's
                // connection. Record the status and try the next host.
                failures.add(host + " returned HTTP " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(host + " was interrupted");
                // Don't keep retrying once the thread is interrupted; report what we have.
                throw new ExchangeRateException(fetchFailureMessage(path, failures), e);
            } catch (IOException e) {
                // Couldn't complete the request to this host; keep the cause and try the next.
                lastCause = e;
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(host + " failed: " + reason);
            }
        }
        throw new ExchangeRateException(fetchFailureMessage(path, failures), lastCause);
    }

    // Issue #3: pure, testable seam that turns the per-host outcomes into one diagnostic
    // message. Keeping it network-free lets the tests pin the reporting behaviour directly.
    static String fetchFailureMessage(String path, List<String> failures) {
        if (failures.isEmpty()) {
            return "Unable to reach the exchange rate service for " + path + ".";
        }
        return "Could not fetch " + path + " from the exchange rate service: "
                + String.join("; ", failures) + ".";
    }

    
    public static class ExchangeRateException extends RuntimeException {
        public ExchangeRateException(String message) {
            super(message);
        }

        // Issue #3: chain the underlying network failure so the stack trace / logs keep the
        // real cause (a timeout, connection reset, ...) instead of discarding it.
        public ExchangeRateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
