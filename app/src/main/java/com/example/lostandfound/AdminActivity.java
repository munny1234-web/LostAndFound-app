package com.example.lostandfound;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.*;

public class AdminActivity extends AppCompatActivity {

    TextView tvTotalLost, tvTotalFound, tvTotalUsers, tvTotalChats;
    ListView listReports;
    Button btnBack;
    DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AdminUtils.isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser())) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_admin);

        dbRef = FirebaseDatabase.getInstance().getReference();

        tvTotalLost = findViewById(R.id.tvTotalLost);
        tvTotalFound = findViewById(R.id.tvTotalFound);
        tvTotalUsers = findViewById(R.id.tvTotalUsers);
        tvTotalChats = findViewById(R.id.tvTotalChats);
        listReports = findViewById(R.id.listReports);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btnManagePosts).setOnClickListener(v -> startActivity(new android.content.Intent(this, AdminPostsActivity.class)));
        findViewById(R.id.btnManageUsers).setOnClickListener(v -> startActivity(new android.content.Intent(this, AdminUsersActivity.class)));
        findViewById(R.id.btnAnalytics).setOnClickListener(v -> startActivity(new android.content.Intent(this, AdminAnalyticsActivity.class)));

        loadStats();
        loadReports();
    }

    void loadStats() {
        // Total items
        dbRef.child("items").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                int lost = 0, found = 0;
                for (DataSnapshot s : snap.getChildren()) {
                    String type = s.child("type").getValue(String.class);
                    if ("lost".equals(type)) lost++;
                    else if ("found".equals(type)) found++;
                }
                tvTotalLost.setText(String.valueOf(lost));
                tvTotalFound.setText(String.valueOf(found));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        // Total users
        dbRef.child("users").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                tvTotalUsers.setText(String.valueOf(snap.getChildrenCount()));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        // Total chats
        dbRef.child("chats").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                tvTotalChats.setText(String.valueOf(snap.getChildrenCount()));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    void loadReports() {
        dbRef.child("reports").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                ArrayList<String[]> list = new ArrayList<>();
                for (DataSnapshot s : snap.getChildren()) {
                    String itemId = s.child("itemId").getValue(String.class);
                    String itemTitle = s.child("itemTitle").getValue(String.class);
                    String reason = s.child("reason").getValue(String.class);
                    String reportedBy = s.child("reportedBy").getValue(String.class);
                    String status = s.child("status").getValue(String.class);
                    list.add(new String[]{
                            itemTitle != null ? itemTitle : "Unknown",
                            reason != null ? reason : "",
                            reportedBy != null ? reportedBy : "",
                            status != null ? status : "pending",
                            s.getKey(),
                            itemId != null ? itemId : ""
                    });
                }

                if (list.isEmpty()) {
                    list.add(new String[]{"No reports yet", "All posts are clean!", "", "clean", "", ""});
                }

                ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(AdminActivity.this,
                        R.layout.item_report, list) {
                    @NonNull @Override
                    public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                        if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_report, parent, false);
                        String[] item = list.get(pos);
                        ((TextView) cv.findViewById(R.id.tvReportTitle)).setText(item[0]);
                        ((TextView) cv.findViewById(R.id.tvReportReason)).setText(item[1]);
                        ((TextView) cv.findViewById(R.id.tvReportStatus)).setText(item[3]);

                        Button btnDismiss = cv.findViewById(R.id.btnDismiss);
                        Button btnDelete = cv.findViewById(R.id.btnDelete);

                        if (item[4].isEmpty()) {
                            btnDismiss.setVisibility(View.GONE);
                            btnDelete.setVisibility(View.GONE);
                        } else {
                            btnDismiss.setOnClickListener(v -> {
                                dbRef.child("reports").child(item[4]).child("status").setValue("dismissed");
                                Toast.makeText(AdminActivity.this, "Report dismissed!", Toast.LENGTH_SHORT).show();
                            });
                            btnDelete.setOnClickListener(v ->
                                    new android.app.AlertDialog.Builder(AdminActivity.this)
                                            .setTitle("Delete Post")
                                            .setMessage("Delete reported post \"" + item[0] + "\"?")
                                            .setPositiveButton("Delete", (d, w) -> {
                                                dbRef.child("reports").child(item[4]).child("status").setValue("deleted");
                                                // Actually remove the reported item so it disappears from the feed
                                                String itemId = item[5];
                                                if (!itemId.isEmpty()) {
                                                    dbRef.child("items").child(itemId).removeValue();
                                                }
                                                Toast.makeText(AdminActivity.this, "Post deleted!", Toast.LENGTH_SHORT).show();
                                            })
                                            .setNegativeButton("Cancel", null)
                                            .show()
                            );
                        }
                        return cv;
                    }
                };
                listReports.setAdapter(adapter);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
}
