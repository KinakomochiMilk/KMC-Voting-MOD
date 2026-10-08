package jp.kinakomochi.kmcvoting.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KMCVotingAPI {
    public interface VoteListener { void onVote(Vote vote); }
    private static final List<VoteListener> LISTENERS = new CopyOnWriteArrayList<VoteListener>();
    private static String token;
    private static String publicKeyPem;

    private KMCVotingAPI() {}
    public static void init(String tokenValue, String publicKeyValue) { token = tokenValue; publicKeyPem = publicKeyValue; }
    public static void registerVoteListener(VoteListener listener) { if (listener != null) LISTENERS.add(listener); }
    public static void unregisterVoteListener(VoteListener listener) { LISTENERS.remove(listener); }
    public static String getPublicKeyPem() { return publicKeyPem; }
    public static Vote createVote(String username, String address, long timestamp, String serviceName) { return new Vote(username, address, timestamp, serviceName); }
    public static void fireVote(Vote vote) { for (VoteListener l : LISTENERS) { try { l.onVote(vote); } catch (Throwable ignored) {} } }

    public static final class Vote {
        private final String username, address, serviceName;
        private final long timestamp;
        public Vote(String username, String address, long timestamp, String serviceName) { this.username=username; this.address=address; this.timestamp=timestamp; this.serviceName=serviceName; }
        public String getUsername(){return username;}
        public String getAddress(){return address;}
        public long getTimestamp(){return timestamp;}
        public String getServiceName(){return serviceName;}
    }
}
