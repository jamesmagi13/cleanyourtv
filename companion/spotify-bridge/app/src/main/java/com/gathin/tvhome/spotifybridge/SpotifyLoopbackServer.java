package com.gathin.tvhome.spotifybridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded loopback HTTP server (RFC 8252 Section 7.3) listening on 127.0.0.1:8888.
 * Catches the OAuth 2.0 PKCE redirect from the browser (http://127.0.0.1:8888/callback).
 * Bound strictly to localhost; rejects all non-loopback connections.
 */
public final class SpotifyLoopbackServer {

    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final int LOOPBACK_PORT = 8888;
    public static final String CALLBACK_PATH = "/callback";
    public static final String DEFAULT_REDIRECT_URI = "http://" + LOOPBACK_HOST + ":" + LOOPBACK_PORT + CALLBACK_PATH;

    public interface CallbackListener {
        void onSuccess(String code, String state);
        void onError(String error, String state);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "spotify-loopback"));
    private ServerSocket serverSocket;
    private volatile boolean running;

    /**
     * Starts listening asynchronously for a single OAuth callback.
     * Automatically stops and closes upon receiving the callback or timing out.
     */
    public synchronized void start(int timeoutMs, CallbackListener listener) {
        stop();
        running = true;
        executor.execute(() -> listen(timeoutMs, listener));
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }
    }

    private void listen(int timeoutMs, CallbackListener listener) {
        try {
            serverSocket = new ServerSocket(LOOPBACK_PORT, 1, InetAddress.getByName(LOOPBACK_HOST));
            serverSocket.setSoTimeout(timeoutMs);

            while (running) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    if (running && listener != null) {
                        listener.onError("timeout", null);
                    }
                    break;
                }

                if (!running) {
                    if (client != null) client.close();
                    break;
                }

                handleClient(client, listener);
                break; // One-shot callback
            }
        } catch (Exception e) {
            if (running && listener != null) {
                listener.onError("server_error: " + e.getMessage(), null);
            }
        } finally {
            stop();
        }
    }

    private void handleClient(Socket client, CallbackListener listener) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = client.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            // Format: GET /callback?code=...&state=... HTTP/1.1
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase("GET")) {
                String fullPath = parts[1];
                int queryIdx = fullPath.indexOf('?');
                String path = queryIdx >= 0 ? fullPath.substring(0, queryIdx) : fullPath;
                String query = queryIdx >= 0 ? fullPath.substring(queryIdx + 1) : "";

                if (CALLBACK_PATH.equals(path)) {
                    Map<String, String> params = parseQuery(query);
                    String code = params.get("code");
                    String error = params.get("error");
                    String state = params.get("state");

                    sendResponse(out, error == null);

                    if (listener != null) {
                        if (code != null) {
                            listener.onSuccess(code, state);
                        } else {
                            listener.onError(error != null ? error : "missing_code", state);
                        }
                    }
                    return;
                }
            }

            sendNotFound(out);
        } catch (Exception ignored) {
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = urlDecode(pair.substring(0, eq));
                String value = urlDecode(pair.substring(eq + 1));
                map.put(key, value);
            }
        }
        return map;
    }

    private static String urlDecode(String val) {
        try {
            return URLDecoder.decode(val, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return val;
        }
    }

    private static void sendResponse(OutputStream out, boolean success) throws Exception {
        String title = success ? "CleanYourTV: Spotify Conectado" : "CleanYourTV: Erro de Autenticação";
        String message = success
                ? "Autenticação concluída com sucesso! Pode fechar esta janela e voltar à sua TV."
                : "A autenticação foi cancelada ou falhou. Pode fechar esta janela.";

        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<style>body{font-family:sans-serif;text-align:center;padding:50px;background:#121212;color:#ffffff;}"
                + "h2{color:" + (success ? "#1DB954" : "#E74C3C") + ";}</style></head>"
                + "<body><h2>" + title + "</h2><p>" + message + "</p></body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static void sendNotFound(OutputStream out) throws Exception {
        String msg = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(msg.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
}
