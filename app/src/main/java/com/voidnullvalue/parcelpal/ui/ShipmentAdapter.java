package com.voidnullvalue.parcelpal.ui;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.voidnullvalue.parcelpal.R;
import com.voidnullvalue.parcelpal.model.Shipment;

import java.util.ArrayList;
import java.util.List;

public final class ShipmentAdapter extends RecyclerView.Adapter<ShipmentAdapter.Holder> {
    public interface Listener { void onShipmentClicked(Shipment shipment); }

    private final List<Shipment> items = new ArrayList<>();
    private final Listener listener;

    public ShipmentAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Shipment> shipments) {
        items.clear();
        items.addAll(shipments);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shipment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Shipment shipment = items.get(position);
        holder.title.setText(shipment.displayName());
        holder.tracking.setText(shipment.trackingNumber + " · " + shipment.carrierHint);
        holder.status.setText(shipment.statusText == null || shipment.statusText.trim().isEmpty() ? "Not checked" : shipment.statusText);
        String checked = shipment.lastCheckedAt == 0 ? "Never checked" : "Checked " + DateUtils.getRelativeTimeSpanString(shipment.lastCheckedAt);
        String source = shipment.sourceName == null || shipment.sourceName.trim().isEmpty() ? "" : " · via " + shipment.sourceName;
        String estimate = shipment.estimatedDelivery == null || shipment.estimatedDelivery.trim().isEmpty() ? "" : " · ETA " + shipment.estimatedDelivery;
        holder.meta.setText(checked + source + estimate);
        holder.itemView.setOnClickListener(v -> listener.onShipmentClicked(shipment));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView tracking;
        final TextView status;
        final TextView meta;

        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
            tracking = view.findViewById(R.id.trackingNumber);
            status = view.findViewById(R.id.status);
            meta = view.findViewById(R.id.meta);
        }
    }
}
