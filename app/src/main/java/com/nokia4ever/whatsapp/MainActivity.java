package com.nokia4ever.whatsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import android.graphics.Color;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.tabs.TabLayout;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private Toolbar mToolbar;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TabsAccessorAdapter mTabsAccessorAdapter;
    private SharedPreferences sharedPreferences;
    private RequestQueue mQueue;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        mQueue = Volley.newRequestQueue(this);

        mToolbar = findViewById(R.id.main_page_toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getString(R.string.app_name));

        mViewPager = findViewById(R.id.main_tabs_pager);
        mTabsAccessorAdapter = new TabsAccessorAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mTabsAccessorAdapter);

        mTabLayout = findViewById(R.id.main_tabs);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    private long mLastUpdateCheck = 0;

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (now - mLastUpdateCheck > 60_000) {
            mLastUpdateCheck = now;
            checkForUpdate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mQueue != null) mQueue.cancelAll(this);
    }

    private void checkForUpdate() {
        String serverUrl = sharedPreferences.getString("server_url", "");
        if (serverUrl.isEmpty()) return;
        String url = serverUrl + "/api/version";
        mQueue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    String remote = response.optString("version", "");
                    String apkUrl = response.optString("apk_url", "");
                    if (!remote.isEmpty() && !apkUrl.isEmpty()
                            && isNewerVersion(remote, BuildConfig.VERSION_NAME)) {
                        showUpdateDialog(remote, apkUrl);
                    }
                },
                error -> { }
        ));
    }

    private boolean isNewerVersion(String remote, String current) {
        try {
            String[] r = remote.split("\\.");
            String[] c = current.split("\\.");
            int len = Math.max(r.length, c.length);
            for (int i = 0; i < len; i++) {
                int rv = i < r.length ? Integer.parseInt(r[i].trim()) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i].trim()) : 0;
                if (rv > cv) return true;
                if (rv < cv) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void showUpdateDialog(String version, String apkUrl) {
        if (isFinishing()) return;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("New version available")
                .setMessage("Version " + version + " is available. Update now?")
                .setPositiveButton("Update", (dlg, w) -> downloadAndInstall(apkUrl))
                .setNegativeButton("Not now", null)
                .show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#25D366"));
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#9E9E9E"));
    }

    private void downloadAndInstall(String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Install update")
                .setMessage("The browser will open to download the update.\n\nIf prompted, uninstall the current version first, then install the new one.")
                .setPositiveButton("Open", (d, w) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    startActivity(browserIntent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        if (item.getItemId() == R.id.action_version) {
            showVersionDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showVersionDialog() {
        if (isFinishing()) return;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Version")
                .setMessage("Installed version: " + BuildConfig.VERSION_NAME)
                .setPositiveButton("Check for update", (dlg, w) -> checkForUpdateManual())
                .setNegativeButton("Close", null)
                .show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#25D366"));
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#9E9E9E"));
    }

    private void checkForUpdateManual() {
        String serverUrl = sharedPreferences.getString("server_url", "");
        if (serverUrl.isEmpty()) return;

        String url = serverUrl + "/api/version";
        mQueue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    String remote = response.optString("version", "");
                    String apkUrl = response.optString("apk_url", "");
                    if (!remote.isEmpty() && !apkUrl.isEmpty()
                            && isNewerVersion(remote, BuildConfig.VERSION_NAME)) {
                        showUpdateDialog(remote, apkUrl);
                    } else {
                        AlertDialog noUpd = new AlertDialog.Builder(this)
                                .setTitle("No updates available")
                                .setMessage("You already have the latest version (" + BuildConfig.VERSION_NAME + ").")
                                .setPositiveButton("OK", null)
                                .show();
                        noUpd.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#25D366"));
                    }
                },
                error -> new AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Could not reach the server.")
                        .setPositiveButton("OK", null)
                        .show()
        ));
    }

    private void logout() {
        String serverUrl = sharedPreferences.getString("server_url", "");
        String user = sharedPreferences.getString("user", "");

        if (!serverUrl.isEmpty() && !user.isEmpty()) {
            String url = serverUrl + "/api/logout/" + user + "@c.us";
            mQueue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> { },
                    error -> { }
            ));
        }

        sharedPreferences.edit()
                .remove("user")
                .remove("pushname")
                .remove("platform")
                .remove("last_chat_id")
                .apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
