package com.nokia4ever.whatsapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import uk.me.hardill.volley.multipart.MultipartRequest;

public class ImageUploadActivity extends AppCompatActivity {

    String serverUrl;
    WhatsAppUser whatsAppUser;
    Contact selectedContact;
    Bitmap photo;
    ImageView mainImage;
    Button selectBtn, uploadBtn;
    static final int CAMERA_REQUEST = 1888;
    static final int GALLERY_REQUEST = 1889;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_upload);

        serverUrl = getIntent().getStringExtra("ServerUrl");
        whatsAppUser = (WhatsAppUser)getIntent().getSerializableExtra("WhatsAppUser");
        selectedContact = (Contact)getIntent().getSerializableExtra("Contact");

        mainImage = (ImageView)findViewById(R.id.main_image);
        selectBtn = (Button) findViewById(R.id.select_image_btn);
        uploadBtn = (Button) findViewById(R.id.upload_image_btn);

        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                //startActivityForResult(intent, CAMERA_REQUEST);

                //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, GALLERY_REQUEST);

                //startActivityForResult(intent, CAMERA_REQUEST);


            }
        });

        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                RequestQueue requestQueue = Volley.newRequestQueue(ImageUploadActivity.this);
                Map<String, String> headers = new HashMap<String, String>();
                /*
                StringRequest stringRequest = new StringRequest(Request.Method.POST, serverUrl, new Response.Listener<String>(){
                    @Override
                    public void onResponse(String response) {
                        String s = response.trim();
                        if(s.equalsIgnoreCase("Loi")){
                            Toast.makeText(ImageUploadActivity.this, "Uploaded", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            Toast.makeText(ImageUploadActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener(){
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(ImageUploadActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                    }
                }) {
                    @Override
                    protected Map<String, String> getParams() throws AuthFailureError {
                        String image = getStringImage(photo);
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("IMG", image);
                        return params;
                    }
                };
                */
                // https://github.com/hardillb/MultiPartVolley
                String url = serverUrl + "/api/upload";
                MultipartRequest request = new MultipartRequest(url, headers,
                        new Response.Listener<NetworkResponse>() {
                            @Override
                            public void onResponse(NetworkResponse response) {
                                Toast.makeText(ImageUploadActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Toast.makeText(ImageUploadActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                            }
                        });

                request.addPart(new MultipartRequest.FormPart("sender",whatsAppUser.getUser()));
                request.addPart(new MultipartRequest.FormPart("receiver",selectedContact.getId()));
                request.addPart(new MultipartRequest.FilePart("media", "image/jpeg", "test.jpg", getByteImage(photo)));

                requestQueue.add(request);

            }//onClock

        });//onClickListener

    }

    private String getStringImage(Bitmap bm) {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, ba);
        byte[] imageData = ba.toByteArray();
        String encode = Base64.encodeToString(imageData, Base64.DEFAULT);
        return encode;
    }

    private byte[] getByteImage(Bitmap bm) {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, ba);
        byte[] imageData = ba.toByteArray();
        return imageData;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == GALLERY_REQUEST) {
            if (resultCode == RESULT_OK) {
                try {
                    final Uri imageUri = data.getData();
                    final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    photo = BitmapFactory.decodeStream(imageStream);
                    mainImage.setImageBitmap(photo);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(ImageUploadActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(ImageUploadActivity.this, "You haven't picked an image", Toast.LENGTH_SHORT).show();
            }
        }

        else if(requestCode == CAMERA_REQUEST){
            photo = (Bitmap) data.getExtras().get("data");
            mainImage.setImageBitmap(photo);
        }
    }
}
