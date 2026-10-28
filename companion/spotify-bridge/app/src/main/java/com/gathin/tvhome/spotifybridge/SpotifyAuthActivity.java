package com.gathin.tvhome.spotifybridge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Leanback / TV-compatible Activity for Spotify OAuth 2.0 PKCE pairing.
 * Coordinates user interaction, SpotifyAuthSession, SpotifyLoopbackServer,
 * SpotifyApiClient, and SpotifyTokenStore.
 * Ensures the loopback redirect (http://127.0.0.1:8888/callback) is handled securely.
 * Allows TV remote control operation without requiring ADB commands.
 */
public class SpotifyAuthActivity extends Activity {

    public static final String EXTRA_CLIENT_ID = "client_id";

    private SpotifyTokenStore tokenStore;
    private SpotifyApiClient apiClient;
    private SpotifyLoopbackServer loopbackServer;

    private EditText inputClientId;
    private TextView statusView;
    private Button btnConnect;
    private Button btnDisconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        tokenStore = new SpotifyTokenStore(this);
        apiClient = new SpotifyApiClient();
        loopbackServer = new SpotifyLoopbackServer();

        inputClientId = findViewById(R.id.inputClientId);
        statusView = findViewById(R.id.authStatus);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);

        String storedId = tokenStore.getClientId();
        if (storedId != null && !storedId.trim().isEmpty() && inputClientId != null) {
            inputClientId.setText(storedId);
        }

        btnConnect.setOnClickListener(v -> initiateLogin());
        btnDisconnect.setOnClickListener(v -> disconnect());

        updateStatusUI();
    }

    @Override
    protected void onDestroy() {
        if (loopbackServer != null) {
            loopbackServer.stop();
        }
        super.onDestroy();
    }

    private void updateStatusUI() {
        if (statusView == null) return;
        if (tokenStore.hasRefreshToken()) {
            statusView.setText(R.string.auth_status_connected);
            btnConnect.setText(R.string.auth_btn_connect);
            btnDisconnect.setVisibility(View.VISIBLE);
        } else {
            statusView.setText(R.string.auth_status_not_connected);
            btnConnect.setText(R.string.auth_btn_connect);
            btnDisconnect.setVisibility(View.GONE);
        }
    }

    private String resolveClientId() {
        if (inputClientId != null && inputClientId.getText() != null) {
            String entered = inputClientId.getText().toString().trim();
            if (!entered.isEmpty()) {
                return entered;
            }
        }
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_CLIENT_ID)) {
            String id = intent.getStringExtra(EXTRA_CLIENT_ID);
            if (id != null && !id.trim().isEmpty()) {
                return id.trim();
            }
        }
        String storedId = tokenStore.getClientId();
        if (storedId != null && !storedId.trim().isEmpty()) {
            return storedId.trim();
        }
        return getString(R.string.spotify_client_id);
    }

    private void initiateLogin() {
        String clientId = resolveClientId();
        if (clientId == null || clientId.trim().isEmpty() || "default".equalsIgnoreCase(clientId)) {
            if (statusView != null) {
                statusView.setText(R.string.auth_error_no_client_id);
            }
            return;
        }

        String redirectUri = SpotifyLoopbackServer.DEFAULT_REDIRECT_URI;
        String authUrl = SpotifyAuthSession.startSession(clientId, redirectUri);

        if (statusView != null) {
            statusView.setText(R.string.auth_status_waiting);
        }

        loopbackServer.start(120000, (code, state, error) -> {
            if (error != null) {
                runOnUiThread(() -> {
                    if (statusView != null) {
                        statusView.setText("Erro retornado pelo Spotify: " + error);
                    }
                });
                return false;
            }

            try {
                String verifier = SpotifyAuthSession.consumeCallback(code, state);
                SpotifyApiClient.TokenResponse tokenResp = apiClient.exchangeAuthorizationCode(clientId, code, verifier, redirectUri);
                if (tokenResp.success) {
                    tokenStore.saveTokens(clientId, tokenResp.accessToken, tokenResp.refreshToken, tokenResp.expiresIn);
                    runOnUiThread(() -> {
                        updateStatusUI();
                        SpotifyPoller.getInstance(getApplicationContext()).pollImmediately();
                    });
                    return true;
                } else {
                    runOnUiThread(() -> {
                        if (statusView != null) {
                            statusView.setText("Falha na autenticação: " + tokenResp.error);
                        }
                    });
                    return false;
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (statusView != null) {
                        statusView.setText("Erro: " + e.getMessage());
                    }
                });
                return false;
            }
        });

        // Open user's browser for authorization
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        } catch (Exception e) {
            if (statusView != null) {
                statusView.setText("Nenhum navegador encontrado para abrir a autenticação");
            }
            loopbackServer.stop();
        }
    }

    private void disconnect() {
        tokenStore.clear();
        SpotifyNowPlayingProvider.clearPlaybackState(getApplicationContext());
        updateStatusUI();
    }
}
