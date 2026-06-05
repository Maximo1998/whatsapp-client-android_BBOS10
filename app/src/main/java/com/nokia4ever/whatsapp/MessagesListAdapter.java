package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.hdodenhof.circleimageview.CircleImageView;

public class MessagesListAdapter extends ArrayAdapter<Message> {
    private static final String TAG = "MessagesListAdapter";
    private Context context;
    private int resource;
    private WhatsAppUser whatsAppUser;
    private String serverUrl;
    private OnImageClickListener imageClickListener;

    public interface OnImageClickListener {
        void onImageClick(String imageUrl);
    }

    public void setImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }

    public MessagesListAdapter(Context context, int resource, ArrayList<Message> objects,
                               WhatsAppUser whatsAppUser, String serverUrl) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.whatsAppUser = whatsAppUser;
        this.serverUrl = serverUrl;
    }

    /** El servidor ya envía la hora en su zona horaria (fuente de verdad).
     *  Mostramos "HH:mm" tal cual, sin reconvertir zonas (el reloj del
     *  dispositivo BB10 aplica mal el horario de verano). */
    private String formatTimestamp(String serverTime) {
        if (serverTime == null || serverTime.isEmpty()) return "";
        int sp = serverTime.indexOf(' ');
        if (sp >= 0 && serverTime.length() >= sp + 6) {
            return serverTime.substring(sp + 1, sp + 6);
        }
        return serverTime;
    }

    /** Reemplaza el contenido del adapter sin recrearlo (evita perder referencia en la Activity). */
    public void update(ArrayList<Message> messages) {
        clear();
        addAll(messages);
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message msg     = getItem(position);
        String sender   = msg.getSender().replace("@c.us", "");
        String message  = msg.getMessage();
        String senderName = msg.getSenderName();
        String createdAt  = formatTimestamp(msg.getCreatedAt());
        String chatType   = msg.getChatType() != null ? msg.getChatType() : "chat";
        String _id        = msg.getId();
        String mediaFilename = msg.getMediaFilename();

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);

        TextView senderMessageText   = view.findViewById(R.id.sender_message_text);
        TextView receiverMessageText = view.findViewById(R.id.receiver_message_text);
        ImageView senderMessageImage   = view.findViewById(R.id.sender_message_image);
        ImageView receiverMessageImage = view.findViewById(R.id.receiver_message_image);
        TextView senderQuote    = view.findViewById(R.id.sender_quote_text);
        TextView receiverQuote  = view.findViewById(R.id.receiver_quote_text);
        TextView senderReaction   = view.findViewById(R.id.sender_reaction);
        TextView receiverReaction = view.findViewById(R.id.receiver_reaction);

        senderMessageText.setVisibility(View.GONE);
        receiverMessageText.setVisibility(View.GONE);
        senderMessageImage.setVisibility(View.GONE);
        receiverMessageImage.setVisibility(View.GONE);
        senderQuote.setVisibility(View.GONE);
        receiverQuote.setVisibility(View.GONE);
        senderReaction.setVisibility(View.GONE);
        receiverReaction.setVisibility(View.GONE);

        boolean isMine = sender.equals(whatsAppUser.getUser())
                || sender.equalsIgnoreCase("Me")
                || sender.equalsIgnoreCase("me");

        // Cita (reply): bloque con autor + vista previa del mensaje citado.
        String quotedMessage = msg.getQuotedMessage();
        String quotedAuthor  = msg.getQuotedAuthor();
        boolean hasQuote = quotedMessage != null && !quotedMessage.isEmpty();
        if (hasQuote) {
            String quoteLabel = (quotedAuthor != null && !quotedAuthor.isEmpty())
                    ? quotedAuthor + "\n" + quotedMessage : quotedMessage;
            TextView quoteView = isMine ? senderQuote : receiverQuote;
            quoteView.setText(quoteLabel);
            quoteView.setVisibility(View.VISIBLE);
        }

        // Reacción (emoji) sobre este mensaje.
        String reaction = msg.getReaction();
        boolean hasReaction = reaction != null && !reaction.isEmpty();
        if (hasReaction) {
            TextView reactionView = isMine ? senderReaction : receiverReaction;
            reactionView.setText(reaction);
            reactionView.setVisibility(View.VISIBLE);
        }

        if (chatType.equals("chat") || chatType.equals("audio") || chatType.equals("ptt")) {
            if (isMine) {
                senderMessageText.setVisibility(View.VISIBLE);
                senderMessageText.setBackgroundResource(R.drawable.sender_messages);
                senderMessageText.setText(message + "\n" + createdAt);
            } else {
                receiverMessageText.setVisibility(View.VISIBLE);
                receiverMessageText.setBackgroundResource(R.drawable.receiver_messages);
                String label = sender.contains("@g.us") ? senderName + "\n" : "";
                receiverMessageText.setText(label + message + "\n" + createdAt);
            }
        } else if (chatType.equals("image") || chatType.equals("sticker")) {
            // El servidor convierte los stickers webp→png (transparencia + 1er fotograma).
            boolean isSticker = chatType.equals("sticker");
            String ext = isSticker ? ".png" : ".jpg";
            String filename = (mediaFilename != null && !mediaFilename.isEmpty())
                    ? mediaFilename : _id + ext;
            final String imageUrl = serverUrl + "/api/mediafile/" + filename;

            ImageView target = isMine ? senderMessageImage : receiverMessageImage;
            target.setVisibility(View.VISIBLE);

            // .fit() hace que Picasso decodifique la imagen al tamaño de la vista
            // (150dp), no a resolución completa. Clave contra el OutOfMemoryError
            // que cerraba la app al abrir chats con varias fotos.
            if (isSticker) {
                // Sticker: encajar completo (sin recortar) y sin placeholder de persona.
                target.setScaleType(ImageView.ScaleType.FIT_CENTER);
                target.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                Picasso.with(context).load(imageUrl).fit().centerInside().into(target);
            } else {
                target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Picasso.with(context).load(imageUrl)
                        .fit().centerCrop()
                        .placeholder(R.drawable.profile_image).into(target);
            }

            target.setOnClickListener(v -> {
                if (imageClickListener != null) {
                    imageClickListener.onImageClick(imageUrl);
                }
            });
        }

        return view;
    }
}
