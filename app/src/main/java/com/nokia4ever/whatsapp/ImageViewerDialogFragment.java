package com.nokia4ever.whatsapp;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class ImageViewerDialogFragment extends DialogFragment {
    private static final String ARG_BITMAP = "bitmap";
    private Bitmap bitmap;

    public static ImageViewerDialogFragment newInstance(Bitmap bitmap) {
        ImageViewerDialogFragment fragment = new ImageViewerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_BITMAP, bitmap);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            bitmap = getArguments().getParcelable(ARG_BITMAP);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_fullscreen_image, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView imageView = view.findViewById(R.id.fullscreen_image);
        ImageButton closeButton = view.findViewById(R.id.close_button);

        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }

        closeButton.setOnClickListener(v -> dismiss());

        // Simple pinch-zoom: double-tap to zoom in/out
        imageView.setOnClickListener(v -> {
            // Toggle between fitCenter and centerInside
            if (imageView.getScaleType() == ImageView.ScaleType.CENTER_INSIDE) {
                imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            } else {
                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            }
        });
    }
}
