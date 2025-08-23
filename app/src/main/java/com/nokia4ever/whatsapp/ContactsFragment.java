package com.nokia4ever.whatsapp;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;


/**
 * A simple {@link Fragment} subclass.
 */
public class ContactsFragment extends Fragment {
    private static final String TAG = "ContactsFragment";

    private View mGroupFragmentView;
    private ListView mListView;
    private ContactsAdapter adapter;
    private ContactsResponse contactsResponse;
    private AlertDialog progressDialog;
    private int seconds = 60 * 5; //refresh contacts list after every 5 minutes
    private Boolean isTimerEnabled=true;
    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private ArrayList<Contact> contactsList;
    private SearchView searchView;

    private SharedPreferences sharedPreferences;


    public ContactsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        sharedPreferences = getContext().getSharedPreferences("UserPreferences", MODE_PRIVATE);
        serverUrl = sharedPreferences.getString("server_url","");

        whatsAppUser = new WhatsAppUser(
                sharedPreferences.getString("pushname",""),
                sharedPreferences.getString("user",""),
                sharedPreferences.getString("platform","")
        );

        mGroupFragmentView = inflater.inflate(R.layout.fragment_contacts, container, false);

        initializeFields();

        timer();


        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);

                return false;
            }
        });

        return mGroupFragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();
    }



    private void timer(){
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                retrieveAndDisplayContacts();
                if(isTimerEnabled)
                    handler.postDelayed(this, seconds * 1000);
            }
        });
    }

    private void initializeFields() {
        mListView = (ListView) mGroupFragmentView.findViewById(R.id.list_view);
        searchView = mGroupFragmentView.findViewById(R.id.search_text);

        contactsList = new ArrayList<>();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    private void retrieveAndDisplayContacts() {
        try {

            String mobile = whatsAppUser.getUser();

            //progressDialog.show();
            String url = serverUrl + "/api/contacts/" + mobile;
            Log.d(TAG, "Caling retrieveAndDisplayContacts with Url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(getContext());
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
                                contactsResponse = gson.fromJson(response.toString(), ContactsResponse.class);
                                //Toast.makeText(getContext(), chatsResponse.getChats().get(0).getMessage(), Toast.LENGTH_SHORT).show();

                                contactsList = contactsResponse.getContacts();
                                adapter =
                                        new ContactsAdapter(getContext(), contactsList, whatsAppUser, serverUrl);

                                mListView.setAdapter(adapter);

                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString(), ex);
                                Toast.makeText(getContext(), ex.toString(), Toast.LENGTH_LONG).show();
                            }

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            progressDialog.dismiss();

                            String errorMsg = error.getMessage();

                            if (errorMsg == null) {
                                Toast.makeText(getContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                            }

                            // if HTTP status code is 401
                            else if (errorMsg.equals("java.io.IOException: No authentication challenges found")) {
                                Toast.makeText(getContext(), "No User Session found, please login from website", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "Unable to fetch contacts, please check server URL", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );
            requestQueue.add(request);
        } catch (Exception ex){
            Log.e(TAG, ex.getMessage(), ex);
        }

    }


}
