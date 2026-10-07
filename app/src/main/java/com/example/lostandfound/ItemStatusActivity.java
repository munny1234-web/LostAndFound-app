package com.example.lostandfound;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class ItemStatusActivity extends AppCompatActivity {

    TextView tvItemTitle, tvCurrentStatus, btnBack;
    Button btnSearching, btnFound, btnReturned;
    ListView listTimeline;
    DatabaseReference dbRef;
    String itemId, userId;
    String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_status);

        dbRef = FirebaseDatabase.getInstance().getReference();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        itemId = getIntent().getStringExtra("ITEM_ID");
        userId = getIntent().getStringExtra("USER_ID");
        String itemTitle = getIntent().getStringExtra("ITEM_TITLE");

        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);
        btnBack = findViewById(R.id.btnBack);
        btnSearching = findViewById(R.id.btnSearching);
        btnFound = findViewById(R.id.btnFound);
        btnReturned = findViewById(R.id.btnReturned);
        listTimeline = findViewById(R.id.listTimeline);

        tvItemTitle.setText(itemTitle != null ? itemTitle : "Item");
        btnBack.setOnClickListener(v -> finish());

        // Only owner can change status
        boolean isOwner = currentUserId.equals(userId);
        btnSearching.setEnabled(isOwner);
        btnFound.setEnabled(isOwner);
        btnReturned.setEnabled(isOwner);

        if (!isOwner) {
            btnSearching.setAlpha(0.5f);
            btnFound.setAlpha(0.5f);
            btnReturned.setAlpha(0.5f);
        }

        btnSearching.setOnClickListener(v -> updateStatus("🔍 Still Searching"));
        btnFound.setOnClickListener(v -> updateStatus("✅ Item Found"));
        btnReturned.setOnClickListener(v -> updateStatus("🎉 Returned to Owner"));

        loadStatus();
        loadTimeline();
    }

    void updateStatus(String status) {
        if (itemId == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("statusUpdatedAt", System.currentTimeMillis());

        dbRef.child("items").child(itemId).updateChildren(update);

        // Add to timeline
        Map<String, Object> timeline = new HashMap<>();
        timeline.put("status", status);
        timeline.put("timestamp", System.currentTimeMillis());
        timeline.put("updatedBy", currentUserId);
        dbRef.child("items").child(itemId).child("timeline").push().setValue(timeline);

        Toast.makeText(this, "Status updated: " + status, Toast.LENGTH_SHORT).show();
    }

    void loadStatus() {
        if (itemId == null) return;
        dbRef.child("items").child(itemId).child("status")
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    String status = snap.getValue(String.class);
                    tvCurrentStatus.setText(status != null ? status : "🔍 Still Searching");
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }

    void loadTimeline() {
        if (itemId == null) return;
        dbRef.child("items").child(itemId).child("timeline")
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    ArrayList<String[]> list = new ArrayList<>();
                    for (DataSnapshot s : snap.getChildren()) {
                        String status = s.child("status").getValue(String.class);
                        Long ts = s.child("timestamp").getValue(Long.class);
                        String time = ts != null ?
                            new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                .format(new java.util.Date(ts)) : "";
                        list.add(0, new String[]{status != null ? status : "", time});
                    }

                    if (list.isEmpty()) {
                        list.add(new String[]{"📋 Post Created", "Just now"});
                    }

                    ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(ItemStatusActivity.this,
                        R.layout.item_timeline, list) {
                        @NonNull @Override
                        public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                            if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_timeline, parent, false);
                            String[] item = list.get(pos);
                            ((TextView) cv.findViewById(R.id.tvTimelineStatus)).setText(item[0]);
                            ((TextView) cv.findViewById(R.id.tvTimelineTime)).setText(item[1]);
                            return cv;
                        }
                    };
                    listTimeline.setAdapter(adapter);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }
}
