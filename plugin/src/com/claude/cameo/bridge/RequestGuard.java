package com.claude.cameo.bridge;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Per-request access checks applied to every HTTP context before its handler runs.
 *
 * <ol>
 *   <li>The {@code Host} header must name the loopback interface. This blocks DNS
 *       rebinding, where a web page resolves its own hostname to 127.0.0.1 and then
 *       talks to the bridge with a foreign {@code Host} value.</li>
 *   <li>{@code OPTIONS} preflights pass through so the handlers' existing CORS code is
 *       unchanged. No handler ever sets {@code Access-Control-Allow-Origin}, so browsers
 *       cannot read responses and cannot send the token header.</li>
 *   <li>The status and capabilities routes stay open. They return plugin metadata only
 *       and let the Python client report version skew or a missing token precisely.</li>
 *   <li>Everything else must carry {@link BridgeAuth#HEADER} with the shared token.</li>
 * </ol>
 *
 * Pure JDK so it can be unit-tested without the CATIA Magic jars.
 */
public final class RequestGuard {

    /** A rejected request: HTTP status plus the error code/message for {@link HttpBridgeServer#sendError}. */
    public record Rejection(int status, String code, String message) {
    }

    private static final Set<String> ALLOWED_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]", "::1");

    private static final Set<String> OPEN_PATHS = Set.of(
            "/status",
            "/api/v1/status",
            "/capabilities",
            "/api/v1/capabilities");

    private final BridgeAuth auth;

    public RequestGuard(BridgeAuth auth) {
        this.auth = auth;
    }

    /** Returns {@code null} when the request may proceed, otherwise the rejection to send. */
    public Rejection check(String method, String path, Headers headers) {
        String host = headers == null ? null : headers.getFirst("Host");
        if (!isLoopbackHost(host)) {
            return new Rejection(403, "BAD_HOST",
                    "Requests must use Host 127.0.0.1 or localhost; got " + (host == null ? "none" : host));
        }
        if ("OPTIONS".equals(method)) {
            return null;
        }
        if (OPEN_PATHS.contains(stripTrailingSlash(path))) {
            return null;
        }
        String presented = headers.getFirst(BridgeAuth.HEADER);
        if (presented == null || presented.isBlank()) {
            return new Rejection(401, "UNAUTHORIZED",
                    "Missing " + BridgeAuth.HEADER + " header. " + tokenHint());
        }
        if (!auth.matches(presented)) {
            return new Rejection(401, "UNAUTHORIZED",
                    "Invalid " + BridgeAuth.HEADER + " header. " + tokenHint());
        }
        return null;
    }

    /** Filter that applies {@link #check} and short-circuits with a JSON error on rejection. */
    public Filter filter() {
        return new Filter() {
            @Override
            public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                Rejection rejection = check(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders());
                if (rejection != null) {
                    HttpBridgeServer.sendError(exchange, rejection.status(), rejection.code(), rejection.message());
                    return;
                }
                chain.doFilter(exchange);
            }

            @Override
            public String description() {
                return "CameoMCPBridge Host and token guard";
            }
        };
    }

    static boolean isLoopbackHost(String hostHeader) {
        if (hostHeader == null) {
            return false;
        }
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            return false;
        }
        if (host.startsWith("[")) {
            // Bracketed IPv6 literal, optionally followed by :port
            int close = host.indexOf(']');
            if (close < 0) {
                return false;
            }
            host = host.substring(0, close + 1);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
        }
        return ALLOWED_HOSTS.contains(host);
    }

    private static String stripTrailingSlash(String path) {
        if (path == null) {
            return "";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String tokenHint() {
        if (auth.tokenFile() != null) {
            return "Send the contents of " + auth.tokenFile() + " (set CAMEO_BRIDGE_TOKEN_FILE or CAMEO_BRIDGE_TOKEN for the MCP server).";
        }
        return "The plugin token comes from " + auth.source() + "; set CAMEO_BRIDGE_TOKEN for the MCP server to match.";
    }
}
