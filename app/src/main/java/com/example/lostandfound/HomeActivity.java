package com.example.lostandfound;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;
public class HomeActivity extends AppCompatActivity {
    TextView tvUserName, tvProfile, tvNotifications, tvChats, tvMapView, btnSearch;
    TextView tvChatsBadge, tvNotificationsBadge;
    ImageView imgProfileIcon;
    EditText etSearch;
    Button btnAll, btnLost, btnFound;
    ListView listItems;
    LinearLayout navHome, navReportLost, navReportFound, navMyPosts, categoryContainer;
    DatabaseReference dbRef;
    String uid, currentFilter = "all", currentCategory = "";
    boolean isAdmin = false;
    ArrayList<Item> allItems = new ArrayList<>();
    String[] categories = {"All","Electronics","Wallet/Purse","Keys","Phone","Documents","Jewelry","Bag","Clothing","Glasses","Bottle/Container","Umbrella","Toy","Sports Equipment","Musical Instrument","Pet","Other"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference();
        isAdmin = AdminUtils.isAdmin(FirebaseAuth.getInstance().getCurrentUser());
        requestNotificationPermissionIfNeeded();
        tvUserName = findViewById(R.id.tvUserName); tvProfile = findViewById(R.id.tvProfile);
        imgProfileIcon = findViewById(R.id.imgProfileIcon);
        tvNotifications = findViewById(R.id.tvNotifications); etSearch = findViewById(R.id.etSearch);
        tvChats = findViewById(R.id.tvChats);
        tvChatsBadge = findViewById(R.id.tvChatsBadge);
        tvNotificationsBadge = findViewById(R.id.tvNotificationsBadge);
        tvMapView = findViewById(R.id.tvMapView);
        btnSearch = findViewById(R.id.btnSearch); btnAll = findViewById(R.id.btnAll);
        btnLost = findViewById(R.id.btnLost); btnFound = findViewById(R.id.btnFound);
        listItems = findViewById(R.id.listItems); navHome = findViewById(R.id.navHome);
        navReportLost = findViewById(R.id.navReportLost); navReportFound = findViewById(R.id.navReportFound);
        navMyPosts = findViewById(R.id.navMyPosts); categoryContainer = findViewById(R.id.categoryContainer);
        dbRef.child("users").child(uid).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) { if (snap.exists()) tvUserName.setText(snap.getValue(String.class)); }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
        for (String cat : categories) {
            TextView chip = new TextView(this);
            chip.setText(cat); chip.setTextSize(12f);
            chip.setPadding(28, 10, 28, 10);
            chip.setTextColor(0xFF6C63FF);
            chip.setBackgroundResource(R.drawable.chip_default);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMarginEnd(8); chip.setLayoutParams(p);
            chip.setOnClickListener(v -> { currentCategory = cat.equals("All") ? "" : cat; applyFilter(currentFilter, currentCategory, etSearch.getText().toString()); });
            categoryContainer.addView(chip);
        }
        btnAll.setOnClickListener(v -> { currentFilter = "all"; applyFilter(currentFilter, currentCategory, etSearch.getText().toString()); });
        btnLost.setOnClickListener(v -> { currentFilter = "lost"; applyFilter(currentFilter, currentCategory, etSearch.getText().toString()); });
        btnFound.setOnClickListener(v -> { currentFilter = "found"; applyFilter(currentFilter, currentCategory, etSearch.getText().toString()); });
        btnSearch.setOnClickListener(v -> applyFilter(currentFilter, currentCategory, etSearch.getText().toString()));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(currentFilter, currentCategory, s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.layoutProfileIconBtn).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        tvNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        tvChats.setOnClickListener(v -> startActivity(new Intent(this, ChatListActivity.class)));
        tvMapView.setOnClickListener(v -> startActivity(new Intent(this, ItemsMapActivity.class)));
        loadProfileIcon();
        startUnreadBadgeListener();
        navHome.setOnClickListener(v -> {});
        navReportLost.setOnClickListener(v -> startActivity(new Intent(this, ReportLostActivity.class)));
        navReportFound.setOnClickListener(v -> startActivity(new Intent(this, ReportFoundActivity.class)));
        navMyPosts.setOnClickListener(v -> startActivity(new Intent(this, MyPostsActivity.class)));
        dbRef.child("items").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                allItems.clear();
                for (DataSnapshot s : snap.getChildren()) {
                    Item item = new Item(); item.id = s.getKey();
                    item.title = s.child("title").getValue(String.class);
                    item.description = s.child("description").getValue(String.class);
                    item.category = s.child("category").getValue(String.class);
                    item.location = s.child("location").getValue(String.class);
                    item.date = s.child("date").getValue(String.class);
                    item.contact = s.child("contact").getValue(String.class);
                    item.reward = s.child("reward").getValue(String.class);
                    item.imageUrl = s.child("imageUrl").getValue(String.class);
                    item.ocrText = s.child("ocrText").getValue(String.class);
                    item.latitude = s.child("latitude").getValue(Double.class);
                    item.longitude = s.child("longitude").getValue(Double.class);
                    item.labelsText = s.child("labelsText").getValue(String.class);
                    item.images = Item.readImagesMap(s, "images");
                    item.type = s.child("type").getValue(String.class);
                    item.userId = s.child("userId").getValue(String.class);
                    item.userName = s.child("userName").getValue(String.class);
                    Long ts = s.child("timestamp").getValue(Long.class);
                    item.timestamp = ts != null ? ts : 0;
                    allItems.add(0, item);
                }
                applyFilter(currentFilter, currentCategory, etSearch.getText().toString());
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
    void applyFilter(String type, String cat, String search) {
        ArrayList<Item> filtered = new ArrayList<>();
        for (Item item : allItems) {
            boolean tm = type.equals("all") || (item.type != null && item.type.equals(type));
            boolean cm = cat.isEmpty() || (item.category != null && item.category.equalsIgnoreCase(cat));
            boolean sm = true;
            String query = search.trim().toLowerCase();
            if (!query.isEmpty()) {
                String haystack = ((item.title != null ? item.title : "") + " " +
                        (item.description != null ? item.description : "") + " " +
                        (item.category != null ? item.category : "") + " " +
                        (item.location != null ? item.location : "") + " " +
                        (item.ocrText != null ? item.ocrText : "")).toLowerCase();
                for (String token : query.split("\\s+")) {
                    if (!haystack.contains(token)) { sm = false; break; }
                }
            }
            if (tm && cm && sm) filtered.add(item);
        }
        ArrayAdapter<Item> adapter = new ArrayAdapter<Item>(this, R.layout.item_post, filtered) {
            @NonNull @Override
            public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_post, parent, false);
                Item item = filtered.get(pos);
                boolean isLost = "lost".equals(item.type);
                TextView tvType = cv.findViewById(R.id.tvType);
                tvType.setText(isLost ? "LOST" : "FOUND");
                tvType.setTextColor(isLost ? 0xFFFF4444 : 0xFF2ECC71);
                tvType.setBackgroundResource(isLost ? R.drawable.tag_lost : R.drawable.tag_found);
                ((TextView) cv.findViewById(R.id.tvCategory)).setText(item.category != null ? item.category : "");
                ((TextView) cv.findViewById(R.id.tvTitle)).setText(item.title);
                ((TextView) cv.findViewById(R.id.tvLocation)).setText(item.location != null ? item.location : "");
                ((TextView) cv.findViewById(R.id.tvDate)).setText(item.date != null ? item.date : "");
                ((TextView) cv.findViewById(R.id.tvPostedBy)).setText(item.userName != null && !item.userName.isEmpty() ? item.userName : "User");
                ImageView img = cv.findViewById(R.id.imgItem);
                ImageUtils.loadItemImage(HomeActivity.this, item.imageUrl, img, R.drawable.chip_default);

                LinearLayout adminControls = cv.findViewById(R.id.layoutAdminControls);
                if (isAdmin) {
                    adminControls.setVisibility(View.VISIBLE);
                    adminControls.findViewById(R.id.btnAdminEditThisPost).setOnClickListener(v -> showAdminEditDialog(item));
                    adminControls.findViewById(R.id.btnAdminDeleteThisPost).setOnClickListener(v ->
                            new android.app.AlertDialog.Builder(HomeActivity.this)
                                    .setTitle("Delete Post")
                                    .setMessage("Permanently delete \"" + item.title + "\"?")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        dbRef.child("items").child(item.id).removeValue();
                                        Toast.makeText(HomeActivity.this, "✅ Post deleted successfully!", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show()
                    );
                } else {
                    adminControls.setVisibility(View.GONE);
                }
                return cv;
            }
        };
        listItems.setAdapter(adapter);
        listItems.setOnItemClickListener((p, v, pos, id) -> { Intent i = new Intent(HomeActivity.this, ItemDetailsActivity.class); i.putExtra("ITEM", filtered.get(pos)); startActivity(i); });
    }

    // Admin-only: quick inline edit for a post, right from wherever it's shown.
    void showAdminEditDialog(Item item) {
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
                                        .addOnCompleteListener(t -> Toast.makeText(HomeActivity.this,
                                                t.isSuccessful() ? "✅ Post updated successfully!" : "Update failed", Toast.LENGTH_SHORT).show());
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileIcon();
    }

    // Shows the user's real uploaded profile photo in the header icon
    // instead of the generic 👤 placeholder, when one exists.
    void loadProfileIcon() {
        dbRef.child("users").child(uid).child("photoUrl").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                String photoUrl = snap.getValue(String.class);
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    imgProfileIcon.setVisibility(View.VISIBLE);
                    tvProfile.setVisibility(View.GONE);
                    ImageUtils.loadProfileImage(HomeActivity.this, photoUrl, imgProfileIcon);
                } else {
                    imgProfileIcon.setVisibility(View.GONE);
                    tvProfile.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    // Live badge counts on the header's 💬 and 🔔 icons — reuses the same
    // "notifications/{uid}" data the Notifications screen already shows,
    // so no extra tracking system is needed.
    void startUnreadBadgeListener() {
        dbRef.child("notifications").child(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                int unreadTotal = 0;
                int unreadMessages = 0;
                for (DataSnapshot s : snap.getChildren()) {
                    Boolean read = s.child("read").getValue(Boolean.class);
                    if (read != null && read) continue; // already read, skip
                    unreadTotal++;
                    String type = s.child("type").getValue(String.class);
                    if ("message".equals(type)) unreadMessages++;
                }
                setBadge(tvNotificationsBadge, unreadTotal);
                setBadge(tvChatsBadge, unreadMessages);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    void setBadge(TextView badge, int count) {
        if (count <= 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText(count > 9 ? "9+" : String.valueOf(count));
        }
    }

    // Android 13+ (API 33+) hides all notifications, even local ones, unless
    // the user grants this runtime permission. Without it, LostFoundApp's
    // alerts would silently never appear.
    void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}
