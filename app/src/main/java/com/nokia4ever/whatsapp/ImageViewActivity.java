package com.nokia4ever.whatsapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

public class ImageViewActivity extends AppCompatActivity {

    private Message selectedMessage;
    private ImageView largeImageView;

    private String serverUrl;
    private WhatsAppUser whatsAppUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);

        selectedMessage = (Message) getIntent().getSerializableExtra("Message");
        serverUrl = getIntent().getStringExtra("ServerUrl");
        whatsAppUser = (WhatsAppUser) getIntent().getSerializableExtra("WhatsAppUser");

        largeImageView = (ImageView) findViewById(R.id.image_view_large);

        // .fit().centerInside() limita el decode a las dimensiones de la vista:
        // Picasso muestrea la imagen al tamaño del ImageView en vez de cargarla a
        // resolución completa, lo que evita OutOfMemoryError en el heap pequeño del Q20.
        Picasso.with(this)  //Here, this is context.
                .load(serverUrl + "/api/mediafile/" + selectedMessage.getId() + ".jpg")  //Url of the image to load.
                .fit()
                .centerInside()
                .into(largeImageView);

    }
}
