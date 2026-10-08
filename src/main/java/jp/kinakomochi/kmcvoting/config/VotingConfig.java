package jp.kinakomochi.kmcvoting.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public final class VotingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dir;
    private static File file;
    private static File publicKeyFile;
    private static File privateKeyFile;
    private static Settings settings;

    private VotingConfig() {}

    public static synchronized void load(File configDirectory) {
        dir = new File(configDirectory, "kmcvoting");
        if (!dir.exists()) dir.mkdirs();
        file = new File(dir, "config.json");
        publicKeyFile = new File(dir, "public.key");
        privateKeyFile = new File(dir, "private.key");
        try {
            if (file.isFile()) {
                settings = GSON.fromJson(read(file), Settings.class);
            }
        } catch (Exception ignored) {}
        if (settings == null) settings = new Settings();
        if (settings.token == null || settings.token.trim().isEmpty()) settings.token = UUID.randomUUID().toString().replace("-", "");
        if (settings.commands == null || settings.commands.isEmpty()) settings.commands = new ArrayList<String>(Collections.singletonList("say {player} voted on {service}!"));
        save();
        ensureRsaKeys();
    }

    private static void ensureRsaKeys() {
        try {
            if (publicKeyFile.isFile() && privateKeyFile.isFile()) return;
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            writePem(publicKeyFile, "PUBLIC KEY", pair.getPublic().getEncoded());
            writePem(privateKeyFile, "PRIVATE KEY", pair.getPrivate().getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Could not create RSA key pair", e);
        }
    }

    private static void writePem(File target, String type, byte[] bytes) throws IOException {
        String b64 = Base64.getEncoder().encodeToString(bytes);
        StringBuilder out = new StringBuilder();
        out.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < b64.length(); i += 64) out.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        out.append("-----END ").append(type).append("-----\n");
        Files.write(target.toPath(), out.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static String read(File f) throws IOException { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    private static void save() {
        try { Files.write(file.toPath(), GSON.toJson(settings).getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { throw new RuntimeException("Could not save config", e); }
    }

    public static String getHost() { return settings.host; }
    public static int getPort() { return settings.port; }
    public static String getToken() { return settings.token; }
    public static List<String> getCommands() { return Collections.unmodifiableList(settings.commands); }
    public static String getPublicKeyPem() { try { return read(publicKeyFile); } catch (IOException e) { return ""; } }
    public static File getPublicKeyFile() { return publicKeyFile; }
    public static File getPrivateKeyFile() { return privateKeyFile; }

    public static final class Settings {
        public String host = "0.0.0.0";
        public int port = 8192;
        public String token = "";
        public List<String> commands = new ArrayList<String>(Collections.singletonList("say {player} voted on {service}!"));
    }
}
