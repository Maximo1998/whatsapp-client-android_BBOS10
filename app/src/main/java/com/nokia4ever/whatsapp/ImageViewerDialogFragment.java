package com.nokia4ever.whatsapp;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;

/**
 * Visor de imagen a pantalla completa con zoom real:
 *   - doble-tap: alterna entre ajustada y zoom (2.5x) centrado en el punto tocado
 *   - pellizco (pinch): zoom continuo
 *   - arrastre: desplaza la imagen cuando está ampliada
 *
 * La imagen se descarga DENTRO del fragment, escalada al tamaño de pantalla, y los
 * argumentos solo guardan la URL (un String). Antes se pasaba el Bitmap a resolución
 * completa por el Bundle de argumentos, lo que (a) reventaba el heap pequeño del Q20
 * (OOM) y (b) podía lanzar TransactionTooLargeException al guardar el estado del
 * fragment (los argumentos se serializan por Binder, límite ~1 MB).
 *
 * Implementado con Matrix + ScaleGestureDetector + GestureDetector para que
 * funcione en el runtime viejo de BB10 (≈Android 4.3, API 17) sin librerías.
 */
public class ImageViewerDialogFragment extends DialogFragment {
    private static final String ARG_URL = "image_url";
    private static final String REQ_TAG = "image_viewer_req";

    private static final float MAX_SCALE = 5.0f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    private String imageUrl;
    private Bitmap bitmap;            // se descarga bajo demanda, ya escalado a pantalla
    private RequestQueue queue;

    private ImageView imageView;
    private final Matrix matrix = new Matrix();
    private float minScale = 1.0f;     // escala que ajusta la imagen a la pantalla
    private float curScale = 1.0f;     // escala actual (absoluta, sobre el bitmap)

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // Para el arrastre
    private float lastTouchX, lastTouchY;
    private boolean dragging = false;

    public static ImageViewerDialogFragment newInstance(String imageUrl) {
        ImageViewerDialogFragment fragment = new ImageViewerDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, imageUrl);   // solo la URL (ligera) — NUNCA el bitmap
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            imageUrl = getArguments().getString(ARG_URL);
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

        imageView = view.findViewById(R.id.fullscreen_image);
        ImageButton closeButton = view.findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> dismiss());

        imageView.setScaleType(ImageView.ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new TapListener());

        imageView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            if (bitmap == null) return true;   // aún descargando

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    dragging = true;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    // Empieza un gesto multitáctil (pinch): pausar el arrastre.
                    dragging = false;
                    break;
                case MotionEvent.ACTION_POINTER_UP: {
                    // Al levantar un dedo y quedar uno, retomar el arrastre desde la
                    // posición del dedo que queda (evita el "salto" tras el pinch).
                    int upIndex = event.getActionIndex();
                    int keep = (upIndex == 0) ? 1 : 0;
                    if (event.getPointerCount() >= 2) {
                        lastTouchX = event.getX(keep);
                        lastTouchY = event.getY(keep);
                        dragging = true;
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !scaleDetector.isInProgress()
                            && event.getPointerCount() == 1) {
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        matrix.postTranslate(dx, dy);
                        clampPan();
                        imageView.setImageMatrix(matrix);
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
            }
            return true;
        });

        loadImage();
    }

    /** Descarga la imagen escalada al tamaño de pantalla (evita OOM y bitmaps enormes). */
    private void loadImage() {
        if (imageUrl == null || imageUrl.isEmpty()) {
            dismiss();
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int maxW = dm.widthPixels;
        final int maxH = dm.heightPixels;

        // Cola propia con el contexto de aplicación (se detiene en onDestroyView).
        queue = Volley.newRequestQueue(requireContext().getApplicationContext());

        ImageRequest request = new ImageRequest(
                imageUrl,
                response -> {
                    if (!isAdded()) return;
                    bitmap = response;
                    imageView.setImageBitmap(bitmap);
                    // Ajustar cuando la vista ya tenga dimensiones.
                    if (imageView.getWidth() > 0 && imageView.getHeight() > 0) {
                        fitToScreen();
                    } else {
                        imageView.getViewTreeObserver().addOnGlobalLayoutListener(
                                new ViewTreeObserver.OnGlobalLayoutListener() {
                                    @Override
                                    public void onGlobalLayout() {
                                        imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                        fitToScreen();
                                    }
                                });
                    }
                },
                maxW, maxH, ImageView.ScaleType.CENTER_INSIDE, Bitmap.Config.RGB_565,
                error -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Could not load image", Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                });
        request.setTag(REQ_TAG);
        queue.add(request);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (queue != null) {
            queue.cancelAll(REQ_TAG);
            queue.stop();
            queue = null;
        }
        bitmap = null;   // liberar la referencia para el GC
    }

    /** Ajusta la imagen a la pantalla (centrada), como centerInside. */
    private void fitToScreen() {
        if (bitmap == null) return;
        float viewW = imageView.getWidth();
        float viewH = imageView.getHeight();
        float bmpW = bitmap.getWidth();
        float bmpH = bitmap.getHeight();
        if (viewW == 0 || viewH == 0 || bmpW == 0 || bmpH == 0) return;

        // Ajustar a pantalla sin AMPLIAR por encima del tamaño nativo (como
        // centerInside): una imagen pequeña se muestra a 1:1 centrada, no borrosa.
        minScale = Math.min(1.0f, Math.min(viewW / bmpW, viewH / bmpH));
        curScale = minScale;

        matrix.reset();
        matrix.postScale(minScale, minScale);
        // Centrar
        float dx = (viewW - bmpW * minScale) / 2f;
        float dy = (viewH - bmpH * minScale) / 2f;
        matrix.postTranslate(dx, dy);
        imageView.setImageMatrix(matrix);
    }

    /** Evita que la imagen se separe de los bordes al desplazar/ampliar. */
    private void clampPan() {
        if (bitmap == null) return;
        RectF rect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(rect);

        float viewW = imageView.getWidth();
        float viewH = imageView.getHeight();
        float dx = 0, dy = 0;

        if (rect.width() <= viewW) {
            dx = (viewW - rect.width()) / 2f - rect.left;      // centrar horizontal
        } else {
            if (rect.left > 0)            dx = -rect.left;
            else if (rect.right < viewW)  dx = viewW - rect.right;
        }
        if (rect.height() <= viewH) {
            dy = (viewH - rect.height()) / 2f - rect.top;      // centrar vertical
        } else {
            if (rect.top > 0)             dy = -rect.top;
            else if (rect.bottom < viewH) dy = viewH - rect.bottom;
        }
        matrix.postTranslate(dx, dy);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (bitmap == null) return false;
            float factor = detector.getScaleFactor();
            float target = curScale * factor;
            // Limitar entre la escala de ajuste y MAX_SCALE
            float clamped = Math.max(minScale, Math.min(target, minScale * MAX_SCALE));
            factor = clamped / curScale;
            curScale = clamped;
            matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            clampPan();
            imageView.setImageMatrix(matrix);
            return true;
        }
    }

    private class TapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (bitmap == null) return false;
            boolean zoomedOut = curScale <= minScale * 1.05f;
            if (zoomedOut) {
                // Zoom in centrado en el punto tocado. El factor se calcula respecto
                // a la escala ACTUAL (no se asume curScale==minScale) para no derivar.
                float target = minScale * DOUBLE_TAP_SCALE;
                float factor = target / curScale;
                curScale = target;
                matrix.postScale(factor, factor, e.getX(), e.getY());
                clampPan();
            } else {
                fitToScreen();   // volver a ajustar
            }
            imageView.setImageMatrix(matrix);
            return true;
        }
    }
}
