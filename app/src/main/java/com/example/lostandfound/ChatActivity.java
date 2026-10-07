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
public class ChatActivity extends AppCompatActivity {
    ListView listMessages;
    EditText etMessage;
    Button btnSend;
    TextView tvChatName, tvItemName, btnBack;
    DatabaseReference chatRef, dbRef;
    String currentUid, otherUserId, chatId, itemId, itemTitle, otherName;
    String myName = "User";
    ArrayList<Message> messages=new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        currentUid=FirebaseAuth.getInstance().getCurrentUser().getUid();
        otherUserId=getIntent().getStringExtra("OTHER_USER_ID");
        itemId=getIntent().getStringExtra("ITEM_ID");
        itemTitle=getIntent().getStringExtra("ITEM_TITLE");
        otherName=getIntent().getStringExtra("OTHER_USER_NAME");
        if (otherName == null) otherName = "User";
        if (itemTitle == null) itemTitle = "Item";
        chatId=currentUid.compareTo(otherUserId)<0?currentUid+"_"+otherUserId+"_"+itemId:otherUserId+"_"+currentUid+"_"+itemId;
        dbRef=FirebaseDatabase.getInstance().getReference();
        chatRef=dbRef.child("chats").child(chatId);
        listMessages=findViewById(R.id.listMessages); etMessage=findViewById(R.id.etMessage);
        btnSend=findViewById(R.id.btnSend); tvChatName=findViewById(R.id.tvChatName);
        tvItemName=findViewById(R.id.tvItemName); btnBack=findViewById(R.id.btnBack);
        tvChatName.setText(otherName);
        tvItemName.setText(itemTitle + "  ›");
        tvItemName.setPaintFlags(tvItemName.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        tvItemName.setOnClickListener(v -> openItemFromChat());
        markMessageNotificationsRead();
        btnBack.setOnClickListener(v->finish());
        btnSend.setOnClickListener(v->sendMsg());

        // Fetch our own name so the OTHER person's chat list can show who we are
        dbRef.child("users").child(currentUid).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (snap.exists() && snap.getValue(String.class) != null) myName = snap.getValue(String.class);
                updateThreadIndex(null); // register this thread for both people right away
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        chatRef.child("messages").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                messages.clear();
                for(DataSnapshot s:snap.getChildren()) {
                    Message msg=new Message(); msg.id=s.getKey();
                    msg.senderId=s.child("senderId").getValue(String.class);
                    msg.text=s.child("text").getValue(String.class);
                    Long ts=s.child("timestamp").getValue(Long.class); msg.timestamp=ts!=null?ts:0;
                    messages.add(msg);
                }
                ArrayAdapter<Message> adapter=new ArrayAdapter<Message>(ChatActivity.this,R.layout.item_message,messages) {
                    @NonNull @Override
                    public View getView(int pos,@Nullable View cv,@NonNull ViewGroup parent) {
                        if(cv==null) cv=getLayoutInflater().inflate(R.layout.item_message,parent,false);
                        Message msg=messages.get(pos); boolean mine=currentUid.equals(msg.senderId);
                        LinearLayout layout=cv.findViewById(R.id.layoutMessage);
                        TextView tvMsg=cv.findViewById(R.id.tvMessage);
                        tvMsg.setText(msg.text);
                        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)layout.getLayoutParams();
                        if(mine) { lp.gravity=android.view.Gravity.END; layout.setBackgroundResource(R.drawable.btn_purple); tvMsg.setTextColor(0xFFFFFFFF); }
                        else { lp.gravity=android.view.Gravity.START; layout.setBackgroundResource(R.drawable.card_elevated); tvMsg.setTextColor(0xFF1A1A2E); }
                        layout.setLayoutParams(lp); return cv;
                    }
                };
                listMessages.setAdapter(adapter);
                if (!messages.isEmpty()) listMessages.setSelection(messages.size()-1);
                markThreadRead(); // chat is open on screen — everything so far counts as read
            }
            @Override public void onCancelled(@NonNull DatabaseError e){}
        });
    }

    // Marks any unread "New Message" notifications for this exact
    // conversation as read, so the Home header's 💬 badge count updates
    // even if the user opened this chat directly (not via Notifications).
    void markMessageNotificationsRead() {
        dbRef.child("notifications").child(currentUid).orderByChild("senderId").equalTo(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot s : snap.getChildren()) {
                            String type = s.child("type").getValue(String.class);
                            String notifItemId = s.child("itemId").getValue(String.class);
                            Boolean read = s.child("read").getValue(Boolean.class);
                            if ("message".equals(type) && itemId != null && itemId.equals(notifItemId)
                                    && (read == null || !read)) {
                                dbRef.child("notifications").child(currentUid).child(s.getKey()).child("read").setValue(true);
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // Tapping the item name in the header opens that post's own details page.
    void openItemFromChat() {
        if (itemId == null || itemId.isEmpty()) return;
        dbRef.child("items").child(itemId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    Toast.makeText(ChatActivity.this, "This item is no longer available.", Toast.LENGTH_SHORT).show();
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
                item.latitude = snap.child("latitude").getValue(Double.class);
                item.longitude = snap.child("longitude").getValue(Double.class);
                item.type = snap.child("type").getValue(String.class);
                item.userId = snap.child("userId").getValue(String.class);
                item.userName = snap.child("userName").getValue(String.class);
                Long ts = snap.child("timestamp").getValue(Long.class);
                item.timestamp = ts != null ? ts : 0;

                Intent i = new Intent(ChatActivity.this, ItemDetailsActivity.class);
                i.putExtra("ITEM", item);
                startActivity(i);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    void sendMsg() {
        String text=etMessage.getText().toString().trim();
        if(text.isEmpty()) return;
        Map<String,Object> msg=new HashMap<>();
        msg.put("senderId",currentUid); msg.put("text",text); msg.put("timestamp",System.currentTimeMillis());
        chatRef.child("messages").push().setValue(msg).addOnCompleteListener(t->{
            if(t.isSuccessful()) {
                etMessage.setText("");
                updateThreadIndex(text);
            }
        });
    }

    // Stamps "read up to now" on MY side of this thread, so the Chat List
    // (app and website both) can show unread conversations in bold.
    void markThreadRead() {
        dbRef.child("userChats").child(currentUid).child(chatId).child("lastRead")
                .setValue(System.currentTimeMillis());
    }

    // Keeps a lightweight "userChats/{uid}/{chatId}" entry for BOTH participants
    // so the new Chat List screen can show every conversation someone is part of,
    // without needing to scan the whole "chats" tree.
    void updateThreadIndex(String lastMessage) {
        long now = System.currentTimeMillis();
        String preview = lastMessage != null ? lastMessage : "";

        Map<String, Object> mine = new HashMap<>();
        mine.put("otherUserId", otherUserId);
        mine.put("otherUserName", otherName);
        mine.put("itemId", itemId);
        mine.put("itemTitle", itemTitle);
        if (!preview.isEmpty()) { mine.put("lastMessage", preview); mine.put("lastTimestamp", now); }
        mine.put("lastRead", now); // I'm looking at this chat right now, so it's read for me
        dbRef.child("userChats").child(currentUid).child(chatId).updateChildren(mine);

        Map<String, Object> theirs = new HashMap<>();
        theirs.put("otherUserId", currentUid);
        theirs.put("otherUserName", myName);
        theirs.put("itemId", itemId);
        theirs.put("itemTitle", itemTitle);
        if (!preview.isEmpty()) { theirs.put("lastMessage", preview); theirs.put("lastTimestamp", now); }
        dbRef.child("userChats").child(otherUserId).child(chatId).updateChildren(theirs);
    }
}
