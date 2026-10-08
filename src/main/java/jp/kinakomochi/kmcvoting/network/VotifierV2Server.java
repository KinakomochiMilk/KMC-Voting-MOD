package jp.kinakomochi.kmcvoting.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.kinakomochi.kmcvoting.api.KMCVotingAPI;
import org.apache.logging.log4j.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class VotifierV2Server {
    private final String host, token;
    private final int port;
    private final Consumer<KMCVotingAPI.Vote> voteConsumer;
    private final Logger logger;
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "KMCVoting-Vote"); t.setDaemon(true); return t; });
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public VotifierV2Server(String host, int port, String token, Consumer<KMCVotingAPI.Vote> voteConsumer, Logger logger) {
        this.host=host; this.port=port; this.token=token; this.voteConsumer=voteConsumer; this.logger=logger;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(host, port));
                logger.info("KMC Voting Votifier v2 listener started on {}:{}", host, port);
                while (running) {
                    final Socket socket = serverSocket.accept();
                    workers.submit(() -> handle(socket));
                }
            } catch (IOException e) {
                if (running) logger.error("Could not start Votifier v2 listener", e);
            }
        }, "KMCVoting-Acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(5000);
            OutputStream out=s.getOutputStream();
            InputStream in=s.getInputStream();
            String challenge=UUID.randomUUID().toString();
            out.write(("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8)); out.flush();
            byte[] header=new byte[4]; readFully(in, header);
            int magic=((header[0]&255)<<8)|(header[1]&255);
            int len=((header[2]&255)<<8)|(header[3]&255);
            if (magic != 0x733A || len < 2 || len > 65535) { respond(out, "error", "bad_packet", "Invalid Votifier v2 frame"); return; }
            byte[] data=new byte[len]; readFully(in,data);
            JsonObject envelope=new JsonParser().parse(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            String payload=envelope.get("payload").getAsString();
            String signature=envelope.get("signature").getAsString();
            String expected=hmac(payload, token);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) { respond(out,"error","invalid_signature","Signature verification failed"); return; }
            JsonObject vote=new JsonParser().parse(payload).getAsJsonObject();
            String receivedChallenge=vote.has("challenge") ? vote.get("challenge").getAsString() : "";
            if (!challenge.equals(receivedChallenge)) { respond(out,"error","invalid_challenge","Challenge mismatch"); return; }
            String username=vote.get("username").getAsString();
            String address=vote.get("address").getAsString();
            long timestamp=vote.get("timestamp").getAsLong();
            String service=vote.get("serviceName").getAsString();
            logger.info("Vote received: player={}, service={}, address={}", username, service, address);
            voteConsumer.accept(new KMCVotingAPI.Vote(username,address,timestamp,service));
            respond(out,"ok",null,null);
        } catch (Exception e) {
            logger.warn("Vote connection failed: {}", e.toString());
        }
    }

    private static String hmac(String data, String token) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
    private static void readFully(InputStream in, byte[] b) throws IOException { int p=0,n; while(p<b.length && (n=in.read(b,p,b.length-p))!=-1)p+=n; if(p!=b.length)throw new EOFException(); }
    private static void respond(OutputStream out,String status,String cause,String msg)throws IOException{
        JsonObject o=new JsonObject();o.addProperty("status",status);if(cause!=null)o.addProperty("cause",cause);if(msg!=null)o.addProperty("errorMessage",msg);
        out.write(new Gson().toJson(o).getBytes(StandardCharsets.UTF_8));out.flush();
    }
    public synchronized void stop(){running=false;try{if(serverSocket!=null)serverSocket.close();}catch(IOException ignored){}workers.shutdownNow();}
}
