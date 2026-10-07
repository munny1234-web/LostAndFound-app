package com.example.lostandfound;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.*;

public class AdminPostsActivity extends AppCompatActivity {

    ListView listAdminPosts;
    EditText etAdminSearch;
    TextView btnBack, tvPostCount;
    DatabaseReference dbRef;
    ArrayList<Item> allItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AdminUtils.isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser())) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_admin_posts);

        dbRef = FirebaseDatabase.getInstance().getReference();
        listAdminPosts = findViewById(R.id.listAdminPosts);
        etAdminSearch = findViewById(R.id.etAdminSearch);
        btnBack = findViewById(R.id.btnBack);
        tvPostCount = findViewById(R.id.tvPostCount);

        btnBack.setOnClickListener(v -> finish());
        etAdminSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadPosts();
    }

    void loadPosts() {
        dbRef.child("items").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                allItems.clear();
                for (DataSnapshot s : snap.getChildren()) {
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
                    Long ts = s.child("timestamp").getValue(Long.class);
                    item.timestamp = ts != null ? ts : 0;
                    allItems.add(0, item);
                }
                renderList(etAdminSearch.getText().toString());
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    void renderList(String query) {
        String q = query.trim().toLowerCase();
        ArrayList<Item> filtered = new ArrayList<>();
        for (Item item : allItems) {
            if (q.isEmpty() || (item.title != null && item.title.toLowerCase().contains(q))) filtered.add(item);
        }
        tvPostCount.setText(filtered.size() + " post(s)");

        ArrayAdapter<Item> adapter = new ArrayAdapter<Item>(this, R.layout.item_admin_post, filtered) {
            @NonNull @Override
            public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_admin_post, parent, false);
                Item item = filtered.get(pos);
                boolean isLost = "lost".equals(item.type);

                TextView tvType = cv.findViewById(R.id.tvAdminPostType);
                tvType.setText(isLost ? "LOST" : "FOUND");
                tvType.setTextColor(isLost ? 0xFFFF4444 : 0xFF2ECC71);
                tvType.setBackgroundResource(isLost ? R.drawable.tag_lost : R.drawable.tag_found);

                ((TextView) cv.findViewById(R.id.tvAdminPostCategory)).setText(item.category != null ? item.category : "");
                ((TextView) cv.findViewById(R.id.tvAdminPostTitle)).setText(item.title != null ? item.title : "(untitled)");
                ((TextView) cv.findViewById(R.id.tvAdminPostMeta)).setText(
                        "👤 " + (item.userName != null ? item.userName : "Unknown") +
                                "   📍 " + (item.location != null ? item.location : "") +
                                "   📅 " + (item.date != null ? item.date : ""));
                ImageView imgAdminPost = cv.findViewById(R.id.imgAdminPost);
                ImageUtils.loadItemImage(AdminPostsActivity.this, item.imageUrl, imgAdminPost, R.drawable.chip_default);

                cv.findViewById(R.id.btnAdminEditPost).setOnClickListener(v -> showEditDialog(item));
                cv.findViewById(R.id.btnAdminDeletePost).setOnClickListener(v ->
                        new android.app.AlertDialog.Builder(AdminPostsActivity.this)
                                .setTitle("Delete Post")
                                .setMessage("Permanently delete \"" + item.title + "\"? This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) -> {
                                    dbRef.child("items").child(item.id).removeValue();
                                    Toast.makeText(AdminPostsActivity.this, "✅ Post deleted successfully!", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show()
                );
                return cv;
            }
        };
        listAdminPosts.setAdapter(adapter);
    }

    void showEditDialog(Item item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_post, null);
        EditText editTitle = dialogView.findViewById(R.id.editTitle);
        EditText editDescription = dialogView.findViewById(R.id.editDescription);
        EditText editCategory = dialogView.findViewById(R.id.editCategory);
        EditText editLocation = dialogView.findViewById(R.id.editLocation);
        EditText editReward = dialogView.findViewById(R.id.editReward);

        editTitle.setText(item.title);
        editDescription.setText(item.description);
        editCategory.setText(item.category);
        editLocation.setText(item.location);
        editReward.setText(item.reward);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit Post")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String newTitle = editTitle.getText().toString().trim();
                    if (newTitle.isEmpty()) { Toast.makeText(this, "Title can't be empty", Toast.LENGTH_SHORT).show(); return; }

                    // Second confirmation so an accidental tap on Save doesn't silently change the post
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Confirm Changes")
                            .setMessage("Save these changes to \"" + newTitle + "\"?")
                            .setPositiveButton("Yes, Save", (d2, w2) -> {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("title", newTitle);
                                updates.put("description", editDescription.getText().toString().trim());
                                updates.put("category", editCategory.getText().toString().trim());
                                updates.put("location", editLocation.getText().toString().trim());
                                updates.put("reward", editReward.getText().toString().trim());
                                dbRef.child("items").child(item.id).updateChildren(updates)
                                        .addOnCompleteListener(t -> Toast.makeText(AdminPostsActivity.this,
                                                t.isSuccessful() ? "✅ Post updated successfully!" : "Update failed", Toast.LENGTH_SHORT).show());
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
