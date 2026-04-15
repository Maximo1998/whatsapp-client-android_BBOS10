package com.nokia4ever.whatsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private Boolean isLoggedIn=false;
    private Button btnLogin;
    private EditText txtMobileNo, txtServerUrl;
    //private TextView lblRegisterLink;
    private AlertDialog progressDialog;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        InitializeFields();

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        txtMobileNo.setText(sharedPreferences.getString("mobile_no",""));
        txtServerUrl.setText(sharedPreferences.getString("server_url","http://nokia4ever.com"));

//        lblRegisterLink.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//            }
//        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Login();
            }
        });

//        lblRegisterLink.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Toast.makeText(LoginActivity.this, "Open 'http://nokia4ever.com/login' on your PC and scan the QR code from your iOS or Android device", Toast.LENGTH_LONG).show();
//            }
//        });
    }

    private void Login(){
        String mobile = txtMobileNo.getText().toString();
        final String serverUrl = txtServerUrl.getText().toString();

        if(TextUtils.isEmpty(mobile)){
            Toast.makeText(this, "Please enter the mobile number", Toast.LENGTH_SHORT).show();
            return;
        }

        if(TextUtils.isEmpty(serverUrl)){
            Toast.makeText(this, "Please enter the server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("mobile_no", mobile);
        editor.putString("server_url", serverUrl);
        editor.apply();

        progressDialog.show();
        String url = serverUrl + "/api/login/" + mobile;
        Log.d(TAG, "Login URL: " + url);
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        final Gson gson = new Gson();

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        progressDialog.dismiss();

                        Log.i("Response", response.toString());

                        try {
                            WhatsAppUser whatsAppUser = gson.fromJson(response.toString(), WhatsAppUser.class);
                            String welcomeMsg = "User ["
                                    + whatsAppUser.getPushname() + "] logged in with mobile ["
                                    + whatsAppUser.getUser() + "] from ["
                                    + whatsAppUser.getPlatform() + "]";

                            Toast.makeText(getApplicationContext(), welcomeMsg, Toast.LENGTH_LONG).show();

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("pushname", whatsAppUser.getPushname());
                            editor.putString("user", whatsAppUser.getUser());
                            editor.putString("platform", whatsAppUser.getPlatform());
                            editor.apply();

                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
//                            intent.putExtra("WhatsAppUser", whatsAppUser);
//                            intent.putExtra("ServerUrl", serverUrl);
                            startActivity(intent);

                        } catch (Exception ex){
                            Toast.makeText(getApplicationContext(), ex.toString(), Toast.LENGTH_LONG).show();
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        progressDialog.dismiss();

                        try {
                            //String responseBody = new String(error.networkResponse.data, "utf-8");
                            //JSONObject data = new JSONObject(responseBody);
                            //String message = data.getString("error");
                            String message = error.getMessage();
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                        }


                    }
                }
        );
        requestQueue.add(request);
    }

    private void InitializeFields() {
        btnLogin = (Button) findViewById(R.id.login_button);
        txtMobileNo = (EditText) findViewById(R.id.login_mobile_no);
        txtServerUrl = (EditText) findViewById(R.id.login_server);
        //lblRegisterLink = (TextView) findViewById(R.id.register_link);

        //txtMobileNo.setText("966549014671");
        txtMobileNo.setHint("e.g. 447935670297");
        //txtServerUrl.setText("http://51.20.189.165");
        txtServerUrl.setText("http://nokia4ever.com");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(isLoggedIn){
            SendUserToMainActivity();
        }
    }

    private void SendUserToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
    }
}
