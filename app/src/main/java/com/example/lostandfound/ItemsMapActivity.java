package com.example.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ItemsMapActivity extends FragmentActivity implements OnMapReadyCallback {

    GoogleMap map;
    TextView btnBack, tvMapItemCount;
    DatabaseReference dbRef;
    Map<String, Item> markerItemMap = new HashMap<>(); // marker id -> Item, so taps can open details

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_items_map);

        btnBack = findViewById(R.id.btnBack);
        tvMapItemCount = findViewById(R.id.tvMapItemCount);
        btnBack.setOnClickListener(v -> finish());

        dbRef = FirebaseDatabase.getInstance().getReference();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.setOnMarkerClickListener(marker -> {
            Item item = markerItemMap.get(marker.getId());
            if (item != null) {
                Intent i = new Intent(ItemsMapActivity.this, ItemDetailsActivity.class);
                i.putExtra("ITEM", item);
                startActivity(i);
            }
            return true; // consume the click (don't auto-show the default info window+center)
        });
        loadItemsWithLocation();
    }

    void loadItemsWithLocation() {
        dbRef.child("items").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                int count = 0;

                for (DataSnapshot s : snap.getChildren()) {
                    Double lat = s.child("latitude").getValue(Double.class);
                    Double lng = s.child("longitude").getValue(Double.class);
                    if (lat == null || lng == null) continue; // this post has no map pin, skip it

                    Item item = new Item();
                    item.id = s.getKey();
                    item.title = s.child("title").getValue(String.class);
                    item.description = s.child("description").getValue(String.class);
                    item.category = s.child("category").getValue(String.class);
                    item.location = s.child("location").getValue(String.class);
                    item.date = s.child("date").getValue(String.class);
                    item.contact = s.child("contact").getValue(String.class);
                    item.reward = s.child("reward").getValue(String.class);
                    item.imageUrl = s.child("imageUrl").getValue(String.class);
                    item.type = s.child("type").getValue(String.class);
                    item.userId = s.child("userId").getValue(String.class);
                    item.userName = s.child("userName").getValue(String.class);

                    LatLng position = new LatLng(lat, lng);
                    boolean isLost = "lost".equals(item.type);
                    Marker marker = map.addMarker(new MarkerOptions()
                            .position(position)
                            .title(item.title)
                            .snippet((isLost ? "Lost • " : "Found • ") + (item.category != null ? item.category : ""))
                            .icon(BitmapDescriptorFactory.defaultMarker(isLost
                                    ? BitmapDescriptorFactory.HUE_RED
                                    : BitmapDescriptorFactory.HUE_GREEN)));
                    if (marker != null) markerItemMap.put(marker.getId(), item);

                    boundsBuilder.include(position);
                    count++;
                }

                tvMapItemCount.setText(count + " item(s) with a location");
                if (count > 0) {
                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));
                    } catch (Exception e) {
                        // Only one point, or points too close together — just center on Dhaka as a fallback
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(23.8103, 90.4125), 12f));
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
}
