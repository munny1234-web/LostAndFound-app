package com.example.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class NotificationsActivity extends AppCompatActivity {
    ListView listNotifications;
    TextView btnBack, tvNoNotifications;
    DatabaseReference dbRef;
    String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference();
        listNotifications = findViewById(R.id.listNotifications);
        btnBack = findViewById(R.id.btnBack);
        tvNoNotifications = findViewById(R.id.tvNoNotifications);
        btnBack.setOnClickListener(v -> finish());
        loadNotifications();
    }

    void loadNotifications() {
        dbRef.child("notifications").child(uid)
                .orderByChild("timestamp")
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        ArrayList<String[]> list = new ArrayList<>();
                        for (DataSnapshot s : snap.getChildren()) {
                            String title = s.child("title").getValue(String.class);
                            String msg = s.child("message").getValue(String.class);
                            String icon = s.child("icon").getValue(String.class);
                            Boolean read = s.child("read").getValue(Boolean.class);
                            Long ts = s.child("timestamp").getValue(Long.class);
                            String type = s.child("type").getValue(String.class);
                            String itemId = s.child("itemId").getValue(String.class);
                            String itemTitle = s.child("itemTitle").getValue(String.class);
                            String senderId = s.child("senderId").getValue(String.class);
                            String senderName = s.child("senderName").getValue(String.class);
                            String time = ts != null ?
                                    new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                                            .format(new java.util.Date(ts)) : "Just now";
                            list.add(0, new String[]{
                                    icon != null ? icon : "🔔",
                                    title != null ? title : "",
                                    msg != null ? msg : "",
                                    time,
                                    read != null && read ? "1" : "0",
                                    s.getKey(),
                                    type != null ? type : "",
                                    itemId != null ? itemId : "",
                                    itemTitle != null ? itemTitle : "",
                                    senderId != null ? senderId : "",
                                    senderName != null ? senderName : ""
                            });
                        }

                        if (list.isEmpty()) {
                            tvNoNotifications.setVisibility(View.VISIBLE);
                            listNotifications.setVisibility(View.GONE);
                            return;
                        }

                        tvNoNotifications.setVisibility(View.GONE);
                        listNotifications.setVisibility(View.VISIBLE);

                        // Fetch a profile photo for every notification that actually
                        // came from another person (chat/claim), so those rows show
                        // a real avatar instead of just the icon. System-generated
                        // notifications (matches, etc.) have no senderId and keep
                        // showing their icon as before.
                        Set<String> senderIds = new HashSet<>();
                        for (String[] row : list) if (!row[9].isEmpty()) senderIds.add(row[9]);

                        Map<String, String> photoMap = new HashMap<>();
                        if (senderIds.isEmpty()) { showList(list, photoMap); return; }

                        final int[] remaining = { senderIds.size() };
                        for (String senderId : senderIds) {
                            dbRef.child("users").child(senderId).child("photoUrl")
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override public void onDataChange(@NonNull DataSnapshot ps) {
                                            String photo = ps.getValue(String.class);
                                            if (photo != null && !photo.isEmpty()) photoMap.put(senderId, photo);
                                            if (--remaining[0] == 0) showList(list, photoMap);
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError e) {
                                            if (--remaining[0] == 0) showList(list, photoMap);
                                        }
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    void showList(ArrayList<String[]> list, Map<String, String> photoMap) {
        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(NotificationsActivity.this,
                R.layout.item_notification, list) {
            @NonNull @Override
            public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_notification, parent, false);
                String[] item = list.get(pos);
                ((TextView) cv.findViewById(R.id.tvIcon)).setText(item[0]);
                ((TextView) cv.findViewById(R.id.tvNotifTitle)).setText(item[1]);
                ((TextView) cv.findViewById(R.id.tvNotifMessage)).setText(item[2]);
                ((TextView) cv.findViewById(R.id.tvNotifTime)).setText(item[3]);

                ImageView imgAvatar = cv.findViewById(R.id.imgNotifAvatar);
                TextView tvIcon = cv.findViewById(R.id.tvIcon);
                String photo = item[9].isEmpty() ? null : photoMap.get(item[9]);
                if (photo != null) {
                    imgAvatar.setVisibility(View.VISIBLE);
                    tvIcon.setVisibility(View.GONE);
                    ImageUtils.loadProfileImage(NotificationsActivity.this, photo, imgAvatar);
                } else {
                    imgAvatar.setVisibility(View.GONE);
                    tvIcon.setVisibility(View.VISIBLE);
                }

                // Unread highlight
                if ("0".equals(item[4])) {
                    cv.setBackgroundResource(R.drawable.chip_default);
                } else {
                    cv.setBackgroundResource(R.drawable.card_elevated);
                }
                return cv;
            }
        };
        listNotifications.setAdapter(adapter);

        // Mark as read AND navigate to the relevant screen
        listNotifications.setOnItemClickListener((p, v, pos, id) -> {
            String[] n = list.get(pos);
            String key = n[5], type = n[6], itemId = n[7], itemTitle = n[8], senderId = n[9], senderName = n[10];

            if (!key.isEmpty()) {
                dbRef.child("notifications").child(uid).child(key).child("read").setValue(true);
            }

            if ("message".equals(type) && !senderId.isEmpty()) {
                Intent i = new Intent(NotificationsActivity.this, ChatActivity.class);
                i.putExtra("ITEM_ID", itemId);
                i.putExtra("ITEM_TITLE", itemTitle);
                i.putExtra("OTHER_USER_ID", senderId);
                i.putExtra("OTHER_USER_NAME", senderName.isEmpty() ? "User" : senderName);
                startActivity(i);
            } else if (("match".equals(type) || "status".equals(type) || "claim".equals(type)) && !itemId.isEmpty()) {
                openItem(itemId);
            }
            // "report" notifications have nowhere specific to go (admin-only info)
        });
    }

    // Fetches the full item and opens ItemDetailsActivity (which expects a full
    // Item object, not just an id).
    void openItem(String itemId) {
        dbRef.child("items").child(itemId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    Toast.makeText(NotificationsActivity.this, "This item is no longer available.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Item item = new Item();
                item.id = itemId;
                item.title = snap.child("title").getValue(String.class);
                item.description = snap.child("description").getValue(String.class);
                item.category = snap.child("category").getValue(String.class);
                item.location = snap.child("location").getValue(String.class);
                item.date = snap.child("date").getValue(String.class);
                item.contact = snap.child("contact").getValue(String.class);
                item.reward = snap.child("reward").getValue(String.class);
                item.imageUrl = snap.child("imageUrl").getValue(String.class);
                item.ocrText = snap.child("ocrText").getValue(String.class);
                item.labelsText = snap.child("labelsText").getValue(String.class);
                item.images = Item.readImagesMap(snap, "images");
                item.type = snap.child("type").getValue(String.class);
                item.userId = snap.child("userId").getValue(String.class);
                item.userName = snap.child("userName").getValue(String.class);
                Long ts = snap.child("timestamp").getValue(Long.class);
                item.timestamp = ts != null ? ts : 0;

                Intent i = new Intent(NotificationsActivity.this, ItemDetailsActivity.class);
                i.putExtra("ITEM", item);
                startActivity(i);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
}
