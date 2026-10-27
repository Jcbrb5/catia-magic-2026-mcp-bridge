package com.claude.cameo.bridge;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BridgeAuthTest {

    @Test
    public void generatesTokenFileWhenMissingAndRereadsIt() throws Exception {
        Path dir = Files.createTempDirectory("bridge-auth");
        Path file = dir.resolve("nested").resolve("token");

        BridgeAuth first = BridgeAuth.loadFromFile(file);
        assertTrue(first.wasGenerated());
        assertEquals("file", first.source());
        assertEquals(file, first.tokenFile());
        assertTrue(Files.isRegularFile(file));

        String stored = Files.readString(file, StandardCharsets.UTF_8).trim();
        assertEquals(64, stored.length());
        assertTrue(first.matches(stored));

        BridgeAuth second = BridgeAuth.loadFromFile(file);
        assertFalse(second.wasGenerated());
        assertTrue(second.matches(stored));
    }

    @Test
    public void readTokenStripsBomAndLineEndings() throws Exception {
        Path file = Files.createTempFile("bridge-auth", ".token");
        Files.writeString(file, "﻿secret-value\r\n", StandardCharsets.UTF_8);
        assertEquals("secret-value", BridgeAuth.readToken(file));

        BridgeAuth auth = BridgeAuth.loadFromFile(file);
        assertTrue(auth.matches("secret-value"));
        assertFalse(auth.wasGenerated());
    }

    @Test
    public void matchesIsStrictAndNullSafe() {
        BridgeAuth auth = BridgeAuth.of("abc");
        assertTrue(auth.matches("abc"));
        assertTrue(auth.matches(" abc \n"));
        assertFalse(auth.matches("abd"));
        assertFalse(auth.matches("ab"));
        assertFalse(auth.matches(""));
        assertFalse(auth.matches(null));
    }

    @Test
    public void describeNeverLeaksTheToken() {
        BridgeAuth auth = BridgeAuth.of("super-secret");
        JsonObject json = auth.describe();
        assertTrue(json.get("required").getAsBoolean());
        assertEquals(BridgeAuth.HEADER, json.get("header").getAsString());
        assertEquals("literal", json.get("source").getAsString());
        assertTrue(json.get("tokenFile").isJsonNull());
        assertNull(json.get("token"));
        assertFalse(json.toString().contains("super-secret"));
        assertFalse(auth.describeForLog().contains("super-secret"));
    }

    @Test
    public void macrosEnabledByDefaultAndDisabledByProperty() {
        String previous = System.getProperty(BridgeAuth.MACROS_PROPERTY);
        try {
            System.clearProperty(BridgeAuth.MACROS_PROPERTY);
            assertTrue(BridgeAuth.macrosEnabled());
            System.setProperty(BridgeAuth.MACROS_PROPERTY, "false");
            assertFalse(BridgeAuth.macrosEnabled());
            System.setProperty(BridgeAuth.MACROS_PROPERTY, "FALSE");
            assertFalse(BridgeAuth.macrosEnabled());
            System.setProperty(BridgeAuth.MACROS_PROPERTY, "true");
            assertTrue(BridgeAuth.macrosEnabled());
        } finally {
            if (previous == null) {
                System.clearProperty(BridgeAuth.MACROS_PROPERTY);
            } else {
                System.setProperty(BridgeAuth.MACROS_PROPERTY, previous);
            }
        }
    }
}
