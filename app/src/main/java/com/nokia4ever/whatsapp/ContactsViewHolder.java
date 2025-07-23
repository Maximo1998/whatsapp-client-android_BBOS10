package com.nokia4ever.whatsapp;

import android.view.View;
import android.widget.TextView;

/**
 * Created by saudi on 21/07/2025.
 */

public class ContactsViewHolder implements View.OnClickListener {

    TextView lblContactId;
    TextView lblContactName;
    ItemClickListener itemClickListener;

    public ContactsViewHolder(View view) {

        lblContactId = view.findViewById(R.id.contact_id);
        lblContactName = view.findViewById(R.id.contact_name);

        view.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {
        this.itemClickListener.OnItemClicked(view);
    }

    public void SetItemClickListener(ItemClickListener ic){
        this.itemClickListener = ic;
    }
}
