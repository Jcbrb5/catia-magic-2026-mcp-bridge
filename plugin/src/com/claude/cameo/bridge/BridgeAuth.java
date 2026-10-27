package com.claude.cameo.bridge;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * Shared secret between the plugin and the MCP server.
 *
 * Resolution order:
 * <ol>
 *   <li>Environment variable {@code CAMEO_BRIDGE_TOKEN}</li>
 *   <li>Token file named by system property {@code cameo.mcp.token.file}</li>
 *   <li>Token file at {@code <user.home>/.cameo-mcp-bridge/token}, generated on first start</li>
 * </ol>
 *
 * The token value is never logged or returned over HTTP. Only its source and the file
 * path are exposed through {@link #describe()} so the Python client can point the user at
 * the right file when authentication fails.
 *
 * This class deliberately has no Cameo imports so it can be unit-tested without the
 * CATIA Magic jars.
 */
public final class BridgeAuth {

    public static final String HEADER = "X-Cameo-Bridge-Token";
    public static final String TOKEN_ENV = "CAMEO_BRIDGE_TOKEN";
    public static final String TOKEN_FILE_PROPERTY = "cameo.mcp.token.file";
    public static final String MACROS_PROPERTY = "cameo.mcp.macros";

    private static final Logger LOG = Logger.getLogger(BridgeAuth.class.getName());
    private static final int TOKEN_BYTES = 32;

    private final byte[] token;
    private final String source;
    private final Path tokenFile;
    private final boolean generated;

    private BridgeAuth(String token, String source, Path tokenFile, boolean generated) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.source = source;
        this.tokenFile = tokenFile;
        this.generated = generated;
    }

    /** Resolve the token from the environment, the configured file, or a freshly generated file. */
    public static BridgeAuth load() throws IOException {
        String fromEnv = System.getenv(TOKEN_ENV);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return new BridgeAuth(fromEnv.trim(), "env", null, false);
        }
        return loadFromFile(defaultTokenFile());
    }

    /** Resolve the token from the given file, creating it with a random value when absent. */
    public static BridgeAuth loadFromFile(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            String existing = readToken(file);
            if (!existing.isEmpty()) {
                return new BridgeAuth(existing, "file", file, false);
            }
            LOG.warning("CameoMCPBridge: token file " + file + " is empty; generating a new token");
        }
        String fresh = generateToken();
        writeToken(file, fresh);
        return new BridgeAuth(fresh, "file", file, true);
    }

    /** Build an instance from a literal value (tests and embedding). */
    public static BridgeAuth of(String token) {
        return new BridgeAuth(token, "literal", null, false);
    }

    public static Path defaultTokenFile() {
        String configured = System.getProperty(TOKEN_FILE_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim());
        }
        return Paths.get(System.getProperty("user.home"), ".cameo-mcp-bridge", "token");
    }

    /** Whether the macro execution endpoint is enabled ({@code -Dcameo.mcp.macros=false} disables it). */
    public static boolean macrosEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(MACROS_PROPERTY, "true").trim());
    }

    /** Constant-time comparison of a presented header value against the configured token. */
    public boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        byte[] candidate = presented.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(token, candidate);
    }

    public String source() {
        return source;
    }

    public Path tokenFile() {
        return tokenFile;
    }

    public boolean wasGenerated() {
        return generated;
    }

    /** One-line description for logs. Never contains the token value. */
    public String describeForLog() {
        if (tokenFile == null) {
            return "token from " + source;
        }
        return (generated ? "generated token file " : "token file ") + tokenFile;
    }

    /** JSON block for the status payload. Never contains the token value. */
    public JsonObject describe() {
        JsonObject json = new JsonObject();
        json.addProperty("required", true);
        json.addProperty("header", HEADER);
        json.addProperty("source", source);
        if (tokenFile != null) {
            json.addProperty("tokenFile", tokenFile.toString());
        } else {
            json.add("tokenFile", null);
        }
        json.addProperty("macrosEnabled", macrosEnabled());
        return json;
    }

    static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Read a token file, tolerating a UTF-8 BOM and trailing line endings written by PowerShell. */
    static String readToken(Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.startsWith("﻿")) {
            raw = raw.substring(1);
        }
        return raw.trim();
    }

    private static void writeToken(Path file, String value) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posix) {
            FileAttribute<?> dirPerms = PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------"));
            FileAttribute<?> filePerms = PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------"));
            if (dir != null) {
                Files.createDirectories(dir, dirPerms);
            }
            Files.deleteIfExists(file);
            Files.createFile(file, filePerms);
        } else {
            // On Windows the user profile folder already restricts access to the owner,
            // SYSTEM and Administrators, which matches what .ssh and .aws rely on.
            if (dir != null) {
                Files.createDirectories(dir);
            }
        }
        Files.writeString(file, value + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
