package com.nokia4ever.whatsapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private Toolbar mToolbar;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TabsAccessorAdapter mTabsAccessorAdapter;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);

        mToolbar = findViewById(R.id.main_page_toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle(getString(R.string.app_name));

        mViewPager = findViewById(R.id.main_tabs_pager);
        mTabsAccessorAdapter = new TabsAccessorAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mTabsAccessorAdapter);

        mTabLayout = findViewById(R.id.main_tabs);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    // Menú para el botón de BlackBerry y el overflow de Android
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
        String user      = sharedPreferences.getString("user", "");

        if (!serverUrl.isEmpty() && !user.isEmpty()) {
            String url = serverUrl + "/api/logout/" + user + "@c.us";
            RequestQueue queue = Volley.newRequestQueue(this);
            queue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                    response -> { /* logout confirmed */ },
                    error -> { /* ignore errors, logout anyway */ }
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
