package com.nokia4ever.whatsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int POLL_INTERVAL_MS = 2000;
    private static final int POLL_MAX_ATTEMPTS = 30; // 30 × 2s = 60s máximo

    private Button btnLogin;
    private EditText txtMobileNo, txtServerUrl;
    private AlertDialog progressDialog;
    private SharedPreferences sharedPreferences;
    private RequestQueue queue;
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLoginInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        queue = Volley.newRequestQueue(this);
        initializeFields();

        txtMobileNo.setText(sharedPreferences.getString("mobile_no", ""));
        String savedServer = sharedPreferences.getString("server_url", "");
        txtServerUrl.setText(savedServer.isEmpty() ? "https://" : savedServer);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { startLoginFlow(); }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isLoginInProgress) return;

        String mobile    = sharedPreferences.getString("mobile_no", "");
        String serverUrl = sharedPreferences.getString("server_url", "");
        String savedUser = sharedPreferences.getString("user", "");

        if (!mobile.isEmpty() && !serverUrl.isEmpty() && !savedUser.isEmpty()) {
            progressDialog.show();
            startClientThenPoll(mobile, serverUrl, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isLoginInProgress = false;
        handler.removeCallbacksAndMessages(null);
        if (queue != null) queue.cancelAll(TAG);
    }

    private void startLoginFlow() {
        String mobile    = txtMobileNo.getText().toString().trim();
        String serverUrl = txtServerUrl.getText().toString().trim();

        if (TextUtils.isEmpty(mobile)) {
            Toast.makeText(this, "Please enter your mobile number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(serverUrl) || serverUrl.equals("https://")) {
            Toast.makeText(this, "Please enter the server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isLoginInProgress) return;

        sharedPreferences.edit()
                .putString("mobile_no", mobile)
                .putString("server_url", serverUrl)
                .apply();

        progressDialog.show();
        startClientThenPoll(mobile, serverUrl, false);
    }

    /** Llama a /api/startclient y luego sondea hasta que el cliente WA esté listo. */
    private void startClientThenPoll(final String mobile, final String serverUrl,
                                     final boolean silent) {
        isLoginInProgress = true;
        String url = serverUrl + "/api/startclient/" + mobile;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, "startclient: " + response.toString());
                        pollStatus(mobile, serverUrl, 0, silent);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        isLoginInProgress = false;
                        progressDialog.dismiss();
                        if (!silent) {
                            Toast.makeText(LoginActivity.this,
                                    "Cannot connect to server. Check URL and try again.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );
        req.setTag(TAG);
        queue.add(req);
    }

    /**
     * Sondea /login-status/:mobile cada 2 segundos.
     * Cuando isAuthenticated=true llama a /api/login para obtener los datos del usuario.
     * Timeout a los 60 segundos.
     */
    private void pollStatus(final String mobile, final String serverUrl,
                            final int attempt, final boolean silent) {
        if (attempt >= POLL_MAX_ATTEMPTS) {
            isLoginInProgress = false;
            progressDialog.dismiss();
            if (!silent) {
                Toast.makeText(this,
                        "Timeout. If it's your first login, scan the QR at:\n" + serverUrl + "/login",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        String url = serverUrl + "/login-status/" + mobile;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean isAuthenticated = response.optBoolean("isAuthenticated", false);
                            if (isAuthenticated) {
                                fetchUserInfo(mobile, serverUrl);
                            } else {
                                // No listo aún — reintentar tras 2s
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        pollStatus(mobile, serverUrl, attempt + 1, silent);
                                    }
                                }, POLL_INTERVAL_MS);
                            }
                        } catch (Exception e) {
                            progressDialog.dismiss();
                            if (!silent) Toast.makeText(LoginActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        progressDialog.dismiss();
                        if (!silent) Toast.makeText(LoginActivity.this,
                                "Server error during login", Toast.LENGTH_LONG).show();
                    }
                }
        );
        req.setTag(TAG);
        queue.add(req);
    }

    private void fetchUserInfo(final String mobile, final String serverUrl) {
        String url = serverUrl + "/api/login/" + mobile;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        isLoginInProgress = false;
                        progressDialog.dismiss();
                        try {
                            WhatsAppUser user = gson.fromJson(response.toString(), WhatsAppUser.class);
                            sharedPreferences.edit()
                                    .putString("pushname", user.getPushname())
                                    .putString("user", user.getUser())
                                    .putString("platform", user.getPlatform())
                                    .putString("mobile_no", mobile)
                                    .putString("server_url", serverUrl)
                                    .apply();

                            Toast.makeText(LoginActivity.this,
                                    "Welcome " + user.getPushname() + "!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        } catch (Exception e) {
                            Toast.makeText(LoginActivity.this, e.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        isLoginInProgress = false;
                        progressDialog.dismiss();
                        String msg = "Login failed";
                        try {
                            if (error.networkResponse != null && error.networkResponse.data != null) {
                                msg = new String(error.networkResponse.data, "utf-8");
                            } else if (error.getMessage() != null) {
                                msg = error.getMessage();
                            }
                        } catch (Exception ignored) {}
                        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                }
        );
        req.setTag(TAG);
        queue.add(req);
    }

    private void initializeFields() {
        btnLogin     = findViewById(R.id.login_button);
        txtMobileNo  = findViewById(R.id.login_mobile_no);
        txtServerUrl = findViewById(R.id.login_server);
        txtMobileNo.setHint("e.g. 34677279900");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }
}
