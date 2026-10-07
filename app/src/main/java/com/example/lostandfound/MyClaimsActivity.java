package com.example.lostandfound;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class MyClaimsActivity extends AppCompatActivity {
    ListView listClaims;
    TextView btnBack, tvEmpty;
    DatabaseReference dbRef;
    String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_claims);
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference();
        listClaims = findViewById(R.id.listClaims);
        btnBack = findViewById(R.id.btnBack);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnBack.setOnClickListener(v -> finish());
        loadClaims();
    }

    void loadClaims() {
        dbRef.child("claims").orderByChild("claimantId").equalTo(uid)
            .addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot snap) {
                    ArrayList<String[]> list = new ArrayList<>();
                    for (DataSnapshot s : snap.getChildren()) {
                        String title = s.child("itemTitle").getValue(String.class);
                        String reason = s.child("reason").getValue(String.class);
                        String status = s.child("status").getValue(String.class);
                        Long ts = s.child("timestamp").getValue(Long.class);
                        String time = ts != null ?
                            new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                .format(new java.util.Date(ts)) : "";
                        list.add(0, new String[]{
                            title != null ? title : "Item",
                            reason != null ? reason : "",
                            status != null ? status : "⏳ Pending",
                            time
                        });
                    }

                    if (list.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        listClaims.setVisibility(View.GONE);
                        return;
                    }

                    tvEmpty.setVisibility(View.GONE);
                    listClaims.setVisibility(View.VISIBLE);

                    ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>(MyClaimsActivity.this,
                        R.layout.item_claim, list) {
                        @NonNull @Override
                        public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                            if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_claim, parent, false);
                            String[] item = list.get(pos);
                            ((TextView) cv.findViewById(R.id.tvClaimTitle)).setText(item[0]);
                            ((TextView) cv.findViewById(R.id.tvClaimReason)).setText("Reason: " + item[1]);
                            TextView tvStatus = cv.findViewById(R.id.tvClaimStatus);
                            tvStatus.setText(item[2]);
                            if (item[2].contains("Approved")) tvStatus.setTextColor(0xFF2ECC71);
                            else if (item[2].contains("Rejected")) tvStatus.setTextColor(0xFFFF4444);
                            else tvStatus.setTextColor(0xFF6C63FF);
                            ((TextView) cv.findViewById(R.id.tvClaimDate)).setText(item[3]);
                            return cv;
                        }
                    };
                    listClaims.setAdapter(adapter);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
    }
}
