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

// Note: this removes a user's profile, posts, and notifications from the
// database. It does NOT delete their Firebase Authentication login — doing
// that from the app itself would need a paid Cloud Functions backend with
// the Admin SDK, which isn't part of this project's free-tier setup. The
// person could still log in afterwards, just with their data cleared.
public class AdminUsersActivity extends AppCompatActivity {

    ListView listAdminUsers;
    EditText etAdminUserSearch;
    TextView btnBack, tvUserCount;
    DatabaseReference dbRef;
    ArrayList<String[]> allUsers = new ArrayList<>(); // [uid, name, email, postCount, claimTotal, claimRejected]

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AdminUtils.isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser())) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_admin_users);

        dbRef = FirebaseDatabase.getInstance().getReference();
        listAdminUsers = findViewById(R.id.listAdminUsers);
        etAdminUserSearch = findViewById(R.id.etAdminUserSearch);
        btnBack = findViewById(R.id.btnBack);
        tvUserCount = findViewById(R.id.tvUserCount);

        btnBack.setOnClickListener(v -> finish());
        etAdminUserSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadUsers();
    }

    void loadUsers() {
        dbRef.child("users").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot usersSnap) {
                // Count posts per user in one pass over "items"
                dbRef.child("items").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot itemsSnap) {
                        Map<String, Integer> postCounts = new HashMap<>();
                        for (DataSnapshot s : itemsSnap.getChildren()) {
                            String ownerId = s.child("userId").getValue(String.class);
                            if (ownerId != null) postCounts.merge(ownerId, 1, Integer::sum);
                        }

                        // Fraud-pattern tracking: count claims per user, and how
                        // many of those were rejected — a high rejection rate is
                        // a signal worth an admin's attention.
                        dbRef.child("claims").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot claimsSnap) {
                                Map<String, Integer> claimTotals = new HashMap<>();
                                Map<String, Integer> claimRejected = new HashMap<>();
                                for (DataSnapshot s : claimsSnap.getChildren()) {
                                    String claimantId = s.child("claimantId").getValue(String.class);
                                    String status = s.child("status").getValue(String.class);
                                    if (claimantId == null) continue;
                                    claimTotals.merge(claimantId, 1, Integer::sum);
                                    if (status != null && status.contains("Rejected")) {
                                        claimRejected.merge(claimantId, 1, Integer::sum);
                                    }
                                }

                                allUsers.clear();
                                for (DataSnapshot s : usersSnap.getChildren()) {
                                    String uid = s.getKey();
                                    String name = s.child("name").getValue(String.class);
                                    String email = s.child("email").getValue(String.class);
                                    int postCount = postCounts.getOrDefault(uid, 0);
                                    int claimTotal = claimTotals.getOrDefault(uid, 0);
                                    int rejectedCount = claimRejected.getOrDefault(uid, 0);
                                    allUsers.add(new String[]{
                                            uid,
                                            name != null ? name : "User",
                                            email != null ? email : "",
                                            String.valueOf(postCount),
                                            String.valueOf(claimTotal),
                                            String.valueOf(rejectedCount)
                                    });
                                }
                                renderList(etAdminUserSearch.getText().toString());
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {}
                        });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    void renderList(String query) {
        String q = query.trim().toLowerCase();
        ArrayList<String[]> filtered = new ArrayList<>();
        for (String[] u : allUsers) {
            if (q.isEmpty() || u[1].toLowerCase().contains(q) || u[2].toLowerCase().contains(q)) filtered.add(u);
        }
        tvUserCount.setText(filtered.size() + " user(s)");

        ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(this, R.layout.item_admin_user, filtered) {
            @NonNull @Override
            public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_admin_user, parent, false);
                String[] u = filtered.get(pos);
                ((TextView) cv.findViewById(R.id.tvAdminUserName)).setText(u[1]);
                ((TextView) cv.findViewById(R.id.tvAdminUserEmail)).setText(u[2]);
                ((TextView) cv.findViewById(R.id.tvAdminUserPostCount)).setText(u[3] + " posts");

                int claimTotal = Integer.parseInt(u[4]);
                int claimRejected = Integer.parseInt(u[5]);
                TextView tvClaimStats = cv.findViewById(R.id.tvAdminUserClaimStats);
                if (claimTotal == 0) {
                    tvClaimStats.setText("No claims submitted");
                    tvClaimStats.setTextColor(0xFF888888);
                } else {
                    String base = "📋 " + claimTotal + " claim(s) submitted, " + claimRejected + " rejected";
                    // Flag a suspicious pattern: several claims with most rejected
                    boolean suspicious = claimTotal >= 3 && claimRejected >= (claimTotal * 0.6);
                    if (suspicious) {
                        tvClaimStats.setText("⚠️ " + base + " — high rejection rate");
                        tvClaimStats.setTextColor(0xFFFF4444);
                    } else {
                        tvClaimStats.setText(base);
                        tvClaimStats.setTextColor(0xFF888888);
                    }
                }

                cv.findViewById(R.id.btnAdminDeleteUser).setOnClickListener(v ->
                        new android.app.AlertDialog.Builder(AdminUsersActivity.this)
                                .setTitle("Remove User")
                                .setMessage("Remove \"" + u[1] + "\"'s profile, all their posts, and notifications?\n\n" +
                                        "Note: this does NOT delete their login — they could still sign in, just with a blank profile.")
                                .setPositiveButton("Remove", (d, w) -> removeUserData(u[0], u[1]))
                                .setNegativeButton("Cancel", null)
                                .show()
                );
                return cv;
            }
        };
        listAdminUsers.setAdapter(adapter);
    }

    void removeUserData(String uid, String name) {
        dbRef.child("users").child(uid).removeValue();
        dbRef.child("notifications").child(uid).removeValue();
        dbRef.child("userChats").child(uid).removeValue();

        dbRef.child("items").orderByChild("userId").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        for (DataSnapshot s : snap.getChildren()) {
                            dbRef.child("items").child(s.getKey()).removeValue();
                        }
                        Toast.makeText(AdminUsersActivity.this, name + "'s data removed", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }
}
