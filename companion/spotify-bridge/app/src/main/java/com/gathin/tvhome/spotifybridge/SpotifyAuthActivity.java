package com.gathin.tvhome.spotifybridge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Handles the OAuth 2.0 PKCE redirect from browser: tvhome-spotify://callback?code=...&state=...
 */
public class SpotifyAuthActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }

        Uri data = intent.getData();
        if (data != null && "tvhome-spotify".equals(data.getScheme()) && "callback".equals(data.getHost())) {
            String code = data.getQueryParameter("code");
            String error = data.getQueryParameter("error");
            String state = data.getQueryParameter("state");

            if (code != null) {
                // In production: exchange code with code_verifier at https://accounts.spotify.com/api/token
                // using SpotifyApiClient and store tokens in SpotifyTokenStore.
            }
        }
        finish();
    }
}
