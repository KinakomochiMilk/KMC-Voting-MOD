package jp.kinakomochi.kmcvoting.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class VotingConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private final File directory;
    private final File configFile;
    private final File publicKeyFile;
    private final File privateKeyFile;

    private String host = "0.0.0.0";
    private int port = 8192;
    private String token = "";
    private Map<String, String> commands = new LinkedHashMap<String, String>();

    public VotingConfig(File configDirectory) {
        this.directory = new File(configDirectory, "kmcvoting");
        this.configFile = new File(directory, "config.json");
        this.publicKeyFile = new File(directory, "public.key");
        this.privateKeyFile = new File(directory, "private.key");

        commands.put("default", "say Vote received from {username}");
    }

    private boolean ensureToken() throws IOException {
        if (token != null && !token.trim()
            .isEmpty()) {
            return false;
        }

        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);

        StringBuilder result = new StringBuilder(64);
        for (byte b : randomBytes) {
            result.append(String.format("%02x", b & 0xff));
        }

        token = result.toString();
        return true;
    }

    /**
     * Creates the directory, loads configuration, and prepares the key pair.
     */

    public void load() throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create KMCVoting directory: " + directory);
        }

        loadConfig();

        boolean configChanged = ensureToken();
        ensureKeyPair();

        if (configChanged) {
            saveConfig();
        }
    }

    private void loadConfig() throws IOException {
        if (!configFile.exists()) {
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);

            if (data == null) {
                throw new IOException("config.json is empty or invalid.");
            }

            if (data.host != null && !data.host.trim()
                .isEmpty()) {
                host = data.host;
            }

            if (data.port >= 1 && data.port <= 65535) {
                port = data.port;
            }

            if (data.token != null) {
                token = data.token;
            }

            if (data.commands != null) {
                for (Map.Entry<String, String> entry : data.commands.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        commands.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Could not parse config.json.", e);
        }
    }

    public void saveConfig() throws IOException {
        ConfigData data = new ConfigData();
        data.host = host;
        data.port = port;
        data.token = token;
        data.commands = commands;

        FileWriter writer = new FileWriter(configFile);

        try {
            GSON.toJson(data, writer);
        } finally {
            writer.close();
        }
    }

    /**
     * Generates an RSA key pair only when neither key exists.
     * Never silently replaces existing keys.
     */
    private void ensureKeyPair() throws IOException {
        boolean publicExists = publicKeyFile.exists();
        boolean privateExists = privateKeyFile.exists();

        if (publicExists && privateExists) {
            return;
        }

        if (publicExists || privateExists) {
            throw new IOException(
                "Only one KMCVoting key exists. " + "Restore the matching key pair before continuing.");
        }

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);

            KeyPair pair = generator.generateKeyPair();

            writePem(
                publicKeyFile,
                "PUBLIC KEY",
                pair.getPublic()
                    .getEncoded());
            writePem(
                privateKeyFile,
                "PRIVATE KEY",
                pair.getPrivate()
                    .getEncoded());

            restrictPrivateKeyPermissions();
        } catch (Exception e) {
            throw new IOException("Could not generate RSA key pair.", e);
        }
    }

    private void writePem(File file, String type, byte[] encoded) throws IOException {
        String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' })
            .encodeToString(encoded);

        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n" + "-----END " + type + "-----\n";

        Files.write(file.toPath(), pem.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Restricts permissions on filesystems supporting POSIX permissions.
     * Windows uses its own filesystem access-control mechanism.
     */
    private void restrictPrivateKeyPermissions() {
        try {
            Set<PosixFilePermission> permissions = new java.util.HashSet<PosixFilePermission>();

            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.OWNER_WRITE);

            Files.setPosixFilePermissions(privateKeyFile.toPath(), permissions);
        } catch (UnsupportedOperationException e) {
            // POSIX permissions are not supported on this filesystem.
        } catch (IOException e) {
            // Permission hardening failed; do not expose the key contents.
        }
    }

    public File getDirectory() {
        return directory;
    }

    public File getConfigFile() {
        return configFile;
    }

    public File getPublicKeyFile() {
        return publicKeyFile;
    }

    public File getPrivateKeyFile() {
        return privateKeyFile;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    public Map<String, String> getCommands() {
        return new LinkedHashMap<String, String>(commands);
    }

    private static final class ConfigData {

        String host = "0.0.0.0";
        int port = 8192;
        String token = "";
        Map<String, String> commands = new LinkedHashMap<String, String>();
    }
}
