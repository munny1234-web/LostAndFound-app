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

// Shows every conversation the current user is part of (built from the
// "userChats/{uid}" index that ChatActivity maintains), so people don't have
// to re-open an item just to find and reply in an existing chat.
public class ChatListActivity extends AppCompatActivity {
    ListView listChats;
    TextView btnBack, tvNoChats;
    DatabaseReference dbRef;
    String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference();
        listChats = findViewById(R.id.listChats);
        btnBack = findViewById(R.id.btnBack);
        tvNoChats = findViewById(R.id.tvNoChats);
        btnBack.setOnClickListener(v -> finish());
        loadChats();
    }

    void loadChats() {
        dbRef.child("userChats").child(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                ArrayList<String[]> list = new ArrayList<>();
                for (DataSnapshot s : snap.getChildren()) {
                    String chatId = s.getKey();
                    String otherUserId = s.child("otherUserId").getValue(String.class);
                    String otherUserName = s.child("otherUserName").getValue(String.class);
                    String itemId = s.child("itemId").getValue(String.class);
                    String itemTitle = s.child("itemTitle").getValue(String.class);
                    String lastMessage = s.child("lastMessage").getValue(String.class);
                    Long ts = s.child("lastTimestamp").getValue(Long.class);
                    Long lastRead = s.child("lastRead").getValue(Long.class);
                    // Unread = the other side's last message is newer than the last
                    // time I had this chat open (lastRead is written by ChatActivity
                    // and by the website's chat page, so it stays in sync both ways).
                    boolean unread = ts != null && (lastRead == null || ts > lastRead);
                    String time = ts != null ?
                            new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                                    .format(new java.util.Date(ts)) : "";
                    list.add(new String[]{
                            chatId,
                            otherUserId != null ? otherUserId : "",
                            otherUserName != null ? otherUserName : "User",
                            itemId != null ? itemId : "",
                            itemTitle != null ? itemTitle : "Item",
                            lastMessage != null ? lastMessage : "Say hi! 👋",
                            time,
                            ts != null ? String.valueOf(ts) : "0",
                            unread ? "1" : "0"
                    });
                }

                // Most recently active conversation first
                list.sort((a, b) -> Long.compare(Long.parseLong(b[7]), Long.parseLong(a[7])));

                if (list.isEmpty()) {
                    tvNoChats.setVisibility(View.VISIBLE);
                    listChats.setVisibility(View.GONE);
                    return;
                }
                tvNoChats.setVisibility(View.GONE);
                listChats.setVisibility(View.VISIBLE);

                // Fetch each conversation partner's profile photo (cached per call,
                // one read per unique user) before showing the list, so avatars are
                // real photos instead of the generic 👤 placeholder when available.
                Set<String> ids = new HashSet<>();
                for (String[] row : list) if (!row[1].isEmpty()) ids.add(row[1]);

                Map<String, String> photoMap = new HashMap<>();
                if (ids.isEmpty()) { showList(list, photoMap); return; }

                final int[] remaining = { ids.size() };
                for (String otherId : ids) {
                    dbRef.child("users").child(otherId).child("photoUrl")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot ps) {
                                    String photo = ps.getValue(String.class);
                                    if (photo != null && !photo.isEmpty()) photoMap.put(otherId, photo);
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
        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(ChatListActivity.this,
                R.layout.item_chat_thread, list) {
            @NonNull @Override
            public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_chat_thread, parent, false);
                String[] item = list.get(pos);

                TextView tvName = cv.findViewById(R.id.tvChatUserName);
                TextView tvItem = cv.findViewById(R.id.tvChatItemTitle);
                TextView tvLast = cv.findViewById(R.id.tvChatLastMessage);
                TextView tvTime = cv.findViewById(R.id.tvChatTime);
                ImageView imgAvatar = cv.findViewById(R.id.imgChatAvatar);
                TextView tvEmoji = cv.findViewById(R.id.tvChatAvatarEmoji);

                tvName.setText(item[2]);
                tvItem.setText(item[4]);
                tvLast.setText(item[5]);
                tvTime.setText(item[6]);

                String photo = photoMap.get(item[1]);
                if (photo != null) {
                    imgAvatar.setVisibility(View.VISIBLE);
                    tvEmoji.setVisibility(View.GONE);
                    ImageUtils.loadProfileImage(ChatListActivity.this, photo, imgAvatar);
                } else {
                    imgAvatar.setVisibility(View.GONE);
                    tvEmoji.setVisibility(View.VISIBLE);
                }

                boolean unread = "1".equals(item[8]);
                // ListView recycles rows, so BOTH branches must set the style
                if (unread) {
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvLast.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvLast.setTextColor(0xFF1A1A2E);
                    tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvTime.setTextColor(0xFF6C63FF);
                } else {
                    tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvLast.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvLast.setTextColor(0xFF9A9AB0);
                    tvTime.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvTime.setTextColor(0xFF9A9AB0);
                }
                return cv;
            }
        };
        listChats.setAdapter(adapter);

        listChats.setOnItemClickListener((p, v, pos, id) -> {
            String[] item = list.get(pos);
            Intent i = new Intent(ChatListActivity.this, ChatActivity.class);
            i.putExtra("ITEM_ID", item[3]);
            i.putExtra("ITEM_TITLE", item[4]);
            i.putExtra("OTHER_USER_ID", item[1]);
            i.putExtra("OTHER_USER_NAME", item[2]);
            startActivity(i);
        });
    }
}
