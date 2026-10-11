package jp.kinakomochi.kmcvoting.api;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * KMCVoting API.
 *
 * Other mods can register VoteListener implementations to receive votes.
 */
public final class KMCVotingAPI {

    private static final CopyOnWriteArrayList<VoteListener> LISTENERS = new CopyOnWriteArrayList<VoteListener>();

    private static String token;
    private static String publicKeyPem;

    private KMCVotingAPI() {}

    public interface VoteListener {

        void onVote(Vote vote);
    }

    /**
     * Initializes the API.
     */
    public static void init(String tokenValue, String publicKeyValue) {
        token = tokenValue;
        publicKeyPem = publicKeyValue;
    }

    public static void registerListener(VoteListener listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void unregisterListener(VoteListener listener) {
        LISTENERS.remove(listener);
    }

    public static String getPublicKeyPem() {
        return publicKeyPem;
    }

    /**
     * Creates a vote instance.
     */
    public static Vote createVote(String username, String address, long timestamp, String serviceName) {
        return new Vote(username, address, timestamp, serviceName);
    }

    /**
     * Notifies registered listeners.
     */
    public static void fireVote(Vote vote) {
        if (vote == null) {
            return;
        }

        for (VoteListener listener : LISTENERS) {
            try {
                listener.onVote(vote);
            } catch (Throwable ignored) {
                // An API listener must not prevent other listeners from running.
            }
        }
    }

    public static final class Vote {

        private final String username;
        private final String address;
        private final String serviceName;
        private final long timestamp;

        public Vote(String username, String address, long timestamp, String serviceName) {
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

        public String getServiceName() {
            return serviceName;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
