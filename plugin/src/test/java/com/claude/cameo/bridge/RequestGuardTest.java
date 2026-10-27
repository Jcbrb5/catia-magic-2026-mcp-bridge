package com.claude.cameo.bridge;

import com.sun.net.httpserver.Headers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RequestGuardTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private final RequestGuard guard = new RequestGuard(BridgeAuth.of(TOKEN));

    private static Headers headers(String host, String token) {
        Headers headers = new Headers();
        if (host != null) {
            headers.add("Host", host);
        }
        if (token != null) {
            headers.add(BridgeAuth.HEADER, token);
        }
        return headers;
    }

    @Test
    public void acceptsLoopbackHostsWithAndWithoutPort() {
        assertTrue(RequestGuard.isLoopbackHost("127.0.0.1"));
        assertTrue(RequestGuard.isLoopbackHost("127.0.0.1:18740"));
        assertTrue(RequestGuard.isLoopbackHost("localhost"));
        assertTrue(RequestGuard.isLoopbackHost("LocalHost:18741"));
        assertTrue(RequestGuard.isLoopbackHost("[::1]"));
        assertTrue(RequestGuard.isLoopbackHost("[::1]:18740"));
    }

    @Test
    public void rejectsForeignOrMissingHosts() {
        assertTrue(!RequestGuard.isLoopbackHost(null));
        assertTrue(!RequestGuard.isLoopbackHost(""));
        assertTrue(!RequestGuard.isLoopbackHost("evil.example"));
        assertTrue(!RequestGuard.isLoopbackHost("evil.example:18740"));
        assertTrue(!RequestGuard.isLoopbackHost("127.0.0.1.evil.example"));
        assertTrue(!RequestGuard.isLoopbackHost("192.168.1.10:18740"));
    }

    @Test
    public void badHostIsRejectedBeforeAnythingElse() {
        RequestGuard.Rejection rejection = guard.check("GET", "/api/v1/status", headers("evil.example", TOKEN));
        assertNotNull(rejection);
        assertEquals(403, rejection.status());
        assertEquals("BAD_HOST", rejection.code());

        rejection = guard.check("OPTIONS", "/api/v1/project", headers(null, TOKEN));
        assertNotNull(rejection);
        assertEquals("BAD_HOST", rejection.code());
    }

    @Test
    public void optionsPassesWithoutToken() {
        assertNull(guard.check("OPTIONS", "/api/v1/macros/execute", headers("127.0.0.1:18740", null)));
    }

    @Test
    public void statusAndCapabilitiesAreOpen() {
        for (String path : new String[] {"/status", "/api/v1/status", "/capabilities", "/api/v1/capabilities", "/api/v1/status/"}) {
            assertNull(path, guard.check("GET", path, headers("127.0.0.1:18740", null)));
        }
    }

    @Test
    public void otherRoutesRequireMatchingToken() {
        RequestGuard.Rejection missing = guard.check("GET", "/api/v1/project", headers("localhost:18740", null));
        assertNotNull(missing);
        assertEquals(401, missing.status());
        assertEquals("UNAUTHORIZED", missing.code());

        RequestGuard.Rejection wrong = guard.check("POST", "/api/v1/macros/execute", headers("127.0.0.1", "nope"));
        assertNotNull(wrong);
        assertEquals(401, wrong.status());

        assertNull(guard.check("POST", "/api/v1/macros/execute", headers("127.0.0.1", TOKEN)));
        assertNull(guard.check("GET", "/api/v1/project", headers("127.0.0.1", "  " + TOKEN + "  ")));
    }

    @Test
    public void statusPrefixDoesNotOpenOtherRoutes() {
        RequestGuard.Rejection rejection = guard.check("GET", "/api/v1/status-dump", headers("127.0.0.1", null));
        assertNotNull(rejection);
        assertEquals(401, rejection.status());
    }
}
