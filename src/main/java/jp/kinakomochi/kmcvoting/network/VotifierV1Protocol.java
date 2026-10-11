package jp.kinakomochi.kmcvoting.network;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

public final class VotifierV1Protocol {

    private static final int RSA_BLOCK_SIZE = 256;

    private final PrivateKey privateKey;

    public VotifierV1Protocol(File privateKeyFile) throws IOException, GeneralSecurityException {
        this.privateKey = loadPrivateKey(privateKeyFile);
    }

    private PrivateKey loadPrivateKey(File file) throws IOException, GeneralSecurityException {
        String pem = new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII);

        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] encoded;
        try {
            encoded = Base64.getDecoder()
                .decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid private key PEM.", e);
        }

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
        return KeyFactory.getInstance("RSA")
            .generatePrivate(spec);
    }

    public VoteData decode(byte[] encryptedBlock) throws GeneralSecurityException, IOException {

        if (encryptedBlock == null || encryptedBlock.length != RSA_BLOCK_SIZE) {
            throw new IOException("Votifier V1 packet must be exactly 256 bytes.");
        }

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        byte[] plaintext = cipher.doFinal(encryptedBlock);
        String message = new String(plaintext, StandardCharsets.UTF_8);

        String[] fields = message.split("\n", -1);

        if (fields.length < 5) {
            throw new IOException("Incomplete Votifier V1 packet.");
        }

        String opcode = fields[0].trim();
        String serviceName = fields[1].trim();
        String username = fields[2].trim();
        String address = fields[3].trim();
        String timestampText = fields[4].trim();

        if (!"VOTE".equals(opcode)) {
            throw new IOException("Invalid Votifier V1 opcode.");
        }

        if (serviceName.isEmpty() || username.isEmpty() || address.isEmpty() || timestampText.isEmpty()) {
            throw new IOException("Votifier V1 packet has empty fields.");
        }

        final long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Votifier V1 timestamp.", e);
        }

        if (timestamp <= 0) {
            throw new IOException("Invalid Votifier V1 timestamp.");
        }

        return new VoteData(username, address, timestamp, serviceName);
    }

    public static final class VoteData {

        private final String username;
        private final String address;
        private final long timestamp;
        private final String serviceName;

        public VoteData(String username, String address, long timestamp, String serviceName) {
            this.username = username;
            this.address = address;
            this.timestamp = timestamp;
            this.serviceName = serviceName;
        }

        public String getUsername() {
            return username;
        }

        public String getAddress() {
            return address;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getServiceName() {
            return serviceName;
        }
    }
}
