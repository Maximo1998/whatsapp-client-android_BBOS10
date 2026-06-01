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

    private Button btnLogin;
    private EditText txtMobileNo, txtServerUrl;
    private AlertDialog progressDialog;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        initializeFields();

        txtMobileNo.setText(sharedPreferences.getString("mobile_no", ""));
        txtServerUrl.setText(sharedPreferences.getString("server_url", "http://nokia4ever.com"));

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { login(); }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Auto-login si ya hay sesión guardada
        String savedMobile = sharedPreferences.getString("mobile_no", "");
        String savedServer = sharedPreferences.getString("server_url", "");
        String savedUser   = sharedPreferences.getString("user", "");

        if (!savedMobile.isEmpty() && !savedServer.isEmpty() && !savedUser.isEmpty()) {
            progressDialog.show();
            autoLogin(savedMobile, savedServer);
        }
    }

    /** Arranca el cliente WA en el servidor y luego verifica el login. */
    private void autoLogin(final String mobile, final String serverUrl) {
        String startUrl = serverUrl + "/api/startclient/" + mobile;
        RequestQueue queue = Volley.newRequestQueue(this);

        queue.add(new JsonObjectRequest(Request.Method.GET, startUrl, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, "startclient OK, waiting for client to be ready...");
                        // Dar tiempo al cliente WA para autenticarse con la sesión guardada
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() { verifyLogin(mobile, serverUrl, true); }
                        }, 4000);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "startclient error: " + error.getMessage());
                        progressDialog.dismiss();
                        // No hay servidor o error de red — mostrar formulario
                    }
                }
        ));
    }

    /** Llama a /api/login para confirmar que el cliente WA está listo. */
    private void verifyLogin(final String mobile, final String serverUrl, final boolean isAutoLogin) {
        String url = serverUrl + "/api/login/" + mobile;
        RequestQueue queue = Volley.newRequestQueue(this);
        final Gson gson = new Gson();

        queue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        progressDialog.dismiss();
                        try {
                            WhatsAppUser user = gson.fromJson(response.toString(), WhatsAppUser.class);
                            saveUserAndProceed(user, serverUrl, mobile);
                        } catch (Exception ex) {
                            if (!isAutoLogin) Toast.makeText(getApplicationContext(), ex.toString(), Toast.LENGTH_LONG).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        progressDialog.dismiss();
                        if (!isAutoLogin) {
                            Toast.makeText(getApplicationContext(),
                                    "Unable to connect to server", Toast.LENGTH_LONG).show();
                        }
                        // Auto-login falló silenciosamente — el usuario verá el formulario
                    }
                }
        ));
    }

    private void saveUserAndProceed(WhatsAppUser user, String serverUrl, String mobile) {
        String welcomeMsg = "Welcome " + user.getPushname() + "!";
        Toast.makeText(getApplicationContext(), welcomeMsg, Toast.LENGTH_SHORT).show();

        sharedPreferences.edit()
                .putString("pushname", user.getPushname())
                .putString("user", user.getUser())
                .putString("platform", user.getPlatform())
                .putString("mobile_no", mobile)
                .putString("server_url", serverUrl)
                .apply();

        startActivity(new Intent(LoginActivity.this, MainActivity.class));
    }

    private void login() {
        String mobile    = txtMobileNo.getText().toString().trim();
        String serverUrl = txtServerUrl.getText().toString().trim();

        if (TextUtils.isEmpty(mobile)) {
            Toast.makeText(this, "Please enter mobile number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(serverUrl)) {
            Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        sharedPreferences.edit()
                .putString("mobile_no", mobile)
                .putString("server_url", serverUrl)
                .apply();

        progressDialog.show();
        autoLogin(mobile, serverUrl);
    }

    private void initializeFields() {
        btnLogin    = findViewById(R.id.login_button);
        txtMobileNo = findViewById(R.id.login_mobile_no);
        txtServerUrl = findViewById(R.id.login_server);
        txtMobileNo.setHint("e.g. 34677279900");
        txtServerUrl.setText("http://nokia4ever.com");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }
}
