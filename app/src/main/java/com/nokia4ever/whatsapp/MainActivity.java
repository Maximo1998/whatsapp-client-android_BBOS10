package com.nokia4ever.whatsapp;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.ViewPager;

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
    private long mDownloadId = -1;
    private BroadcastReceiver mDownloadReceiver;

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

    @Override
    protected void onResume() {
        super.onResume();
        checkForUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDownloadReceiver != null) {
            try { unregisterReceiver(mDownloadReceiver); } catch (Exception ignored) {}
            mDownloadReceiver = null;
        }
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
                    boolean newer = isNewerVersion(remote, BuildConfig.VERSION_NAME);
                    Toast.makeText(this,
                            "Versión servidor: " + remote + " | App: " + BuildConfig.VERSION_NAME + " | Actualizar: " + newer,
                            Toast.LENGTH_LONG).show();
                    if (!remote.isEmpty() && !apkUrl.isEmpty() && newer) {
                        showUpdateDialog(remote, apkUrl);
                    }
                },
                error -> Toast.makeText(this, "Error versión: " + error.toString(), Toast.LENGTH_LONG).show()
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
        new AlertDialog.Builder(this)
                .setTitle("Nueva versión disponible")
                .setMessage("Versión " + version + " disponible. ¿Actualizar ahora?")
                .setPositiveButton("Actualizar", (d, w) -> downloadAndInstall(apkUrl))
                .setNegativeButton("Ahora no", null)
                .show();
    }

    private void downloadAndInstall(String apkUrl) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm == null) return;

        // Borrar APK anterior si existe
        File dest = new File(getExternalFilesDir(null), "update.apk");
        if (dest.exists()) dest.delete();

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
        req.setTitle(getString(R.string.app_name) + " – actualización");
        req.setDescription("Descargando versión nueva...");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(this, null, "update.apk");
        mDownloadId = dm.enqueue(req);

        mDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == mDownloadId) {
                    try { unregisterReceiver(this); } catch (Exception ignored) {}
                    mDownloadReceiver = null;
                    installApk();
                }
            }
        };
        registerReceiver(mDownloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void installApk() {
        File apk = new File(getExternalFilesDir(null), "update.apk");
        if (!apk.exists()) return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", apk);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(apk);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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
        return super.onOptionsItemSelected(item);
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
