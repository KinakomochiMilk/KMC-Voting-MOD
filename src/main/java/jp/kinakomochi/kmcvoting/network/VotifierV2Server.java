package jp.kinakomochi.kmcvoting.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class VotifierV2Server {

    private static final int MAGIC = 0x733A;
    private static final int MAX_MESSAGE_SIZE = 65535;
    private static final int SOCKET_TIMEOUT_MS = 5000;

    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger("KMCVoting");

    private final String host;
    private final int port;
    private final String token;
    private final VoteReceiver voteReceiver;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SecureRandom secureRandom = new SecureRandom();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService clientExecutor;

    public VotifierV2Server(String host, int port, String token, VoteReceiver voteReceiver) {

        if (host == null || host.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Host cannot be empty.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port.");
        }
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Votifier V2 token cannot be empty.");
        }
        if (voteReceiver == null) {
            throw new IllegalArgumentException("VoteReceiver cannot be null.");
        }

        this.host = host;
        this.port = port;
        this.token = token;
        this.voteReceiver = voteReceiver;
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);

        try {
            socket.bind(new InetSocketAddress(host, port));
        } catch (IOException e) {
            socket.close();
            throw e;
        }

        serverSocket = socket;
        clientExecutor = Executors.newFixedThreadPool(4);
        running.set(true);

        acceptThread = new Thread(new Runnable() {

            @Override
            public void run() {
                acceptLoop();
            }
        }, "KMCVoting-Votifier-Accept");

        acceptThread.setDaemon(true);
        acceptThread.start();

        LOGGER.info("Votifier V2 listening on " + host + ":" + port);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                final Socket client = serverSocket.accept();

                clientExecutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        handleClient(client);
                    }
                });
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Failed to accept Votifier connection.", e);
                }
            } catch (RuntimeException e) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Could not schedule Votifier client.", e);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());

            String challenge = createChallenge();

            String greeting = "VOTIFIER 2 " + challenge + "\n";

            out.write(greeting.getBytes(StandardCharsets.UTF_8));
            out.flush();

            int magicHigh = in.read();
            int magicLow = in.read();
            int lengthHigh = in.read();
            int lengthLow = in.read();

            if (magicHigh < 0 || magicLow < 0 || lengthHigh < 0 || lengthLow < 0) {
                throw new IOException("Incomplete Votifier frame.");
            }

            int magic = (magicHigh << 8) | magicLow;
            int length = (lengthHigh << 8) | lengthLow;

            if (magic != MAGIC) {
                throw new IOException("Invalid Votifier frame header.");
            }

            if (length <= 0 || length > MAX_MESSAGE_SIZE) {
                throw new IOException("Invalid message length.");
            }

            byte[] messageBytes = readExactly(in, length);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            JsonObject envelope = new JsonParser().parse(message)
                .getAsJsonObject();

            String payload = envelope.get("payload")
                .getAsString();
            String signature = envelope.get("signature")
                .getAsString();

            byte[] expectedSignature = calculateHmac(payload);
            byte[] suppliedSignature;

            try {
                suppliedSignature = Base64.getDecoder()
                    .decode(signature);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid signature encoding.", e);
            }

            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                sendError(out, "INVALID_SIGNATURE", "Signature verification failed.");
                return;
            }

            JsonObject vote = new JsonParser().parse(payload)
                .getAsJsonObject();

            if (!vote.has("challenge") || !challenge.equals(
                vote.get("challenge")
                    .getAsString())) {
                sendError(out, "INVALID_CHALLENGE", "Challenge verification failed.");
                return;
            }

            if (!vote.has("username") || !vote.has("address") || !vote.has("timestamp") || !vote.has("serviceName")) {
                sendError(out, "INVALID_PAYLOAD", "Required vote fields are missing.");
                return;
            }

            String username = vote.get("username")
                .getAsString();
            String address = vote.get("address")
                .getAsString();
            String serviceName = vote.get("serviceName")
                .getAsString();
            long timestamp = vote.get("timestamp")
                .getAsLong();

            if (username.trim()
                .isEmpty()
                || address.trim()
                    .isEmpty()
                || serviceName.trim()
                    .isEmpty()
                || timestamp <= 0) {
                sendError(out, "INVALID_PAYLOAD", "Vote fields are invalid.");
                return;
            }

            voteReceiver.onVote(username, address, timestamp, serviceName);

            JsonObject response = new JsonObject();
            response.addProperty("status", "ok");

            out.write(
                GSON.toJson(response)
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            LOGGER.info("Received Votifier V2 vote for " + username + " from " + serviceName);

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Rejected or failed Votifier connection.", e);
        }
    }

    private byte[] calculateHmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        mac.init(key);
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String createChallenge() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);

        StringBuilder result = new StringBuilder(32);
        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString();
    }

    private byte[] readExactly(BufferedInputStream in, int length) throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(length);
        byte[] chunk = new byte[4096];
        int remaining = length;

        while (remaining > 0) {
            int read = in.read(chunk, 0, Math.min(chunk.length, remaining));

            if (read < 0) {
                throw new IOException("Incomplete Votifier payload.");
            }

            buffer.write(chunk, 0, read);
            remaining -= read;
        }

        return buffer.toByteArray();
    }

    private void sendError(BufferedOutputStream out, String cause, String message) throws IOException {

        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("cause", cause);
        response.addProperty("errorMessage", message);

        out.write(
            GSON.toJson(response)
                .getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error closing Votifier socket.", e);
            }
        }

        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        LOGGER.info("Votifier V2 server stopped.");
    }

    public boolean isRunning() {
        return running.get();
    }

    public interface VoteReceiver {

        void onVote(String username, String address, long timestamp, String serviceName);
    }
}
