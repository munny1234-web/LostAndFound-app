package com.example.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    TextView tvName, tvEmail, tvLostCount, tvFoundCount, tvClaimCount, tvMemberSince;
    // Menu rows are plain LinearLayouts now (not Button) so the app theme's
    // default Material button tint can never repaint their light backgrounds.
    LinearLayout btnMyPosts, btnMyClaims, btnAbout, btnLogout;
    Button btnEditProfile;
    ImageView imgProfile;
    String uid;
    DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference();

        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        tvLostCount = findViewById(R.id.tvLostCount);
        tvFoundCount = findViewById(R.id.tvFoundCount);
        tvClaimCount = findViewById(R.id.tvClaimCount);
        btnMyPosts = findViewById(R.id.btnMyPosts);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnMyClaims = findViewById(R.id.btnMyClaims);
        btnAbout = findViewById(R.id.btnAbout);
        imgProfile = findViewById(R.id.imgProfile);

        loadProfile();

        btnEditProfile.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        btnMyPosts.setOnClickListener(v -> startActivity(new Intent(this, MyPostsActivity.class)));
        btnMyClaims.setOnClickListener(v -> startActivity(new Intent(this, MyClaimsActivity.class)));
        btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        // Web-style admin access: the admin signs in like any normal user, and
        // this section appears only for the admin account. AdminActivity itself
        // re-checks the email again, so this is a shortcut — not the real gate.
        if (AdminUtils.isAdmin(FirebaseAuth.getInstance().getCurrentUser())) {
            findViewById(R.id.tvAdminSectionLabel).setVisibility(android.view.View.VISIBLE);
            LinearLayout btnAdminDashboard = findViewById(R.id.btnAdminDashboard);
            btnAdminDashboard.setVisibility(android.view.View.VISIBLE);
            btnAdminDashboard.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminActivity.class)));
        }
    }

    void loadProfile() {
        dbRef.child("users").child(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                tvName.setText(snap.child("name").getValue(String.class));
                tvEmail.setText(snap.child("email").getValue(String.class));
                String photoUrl = snap.child("photoUrl").getValue(String.class);
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    ImageUtils.loadProfileImage(ProfileActivity.this, photoUrl, imgProfile);
                }
                Long createdAt = snap.child("createdAt").getValue(Long.class);
                if (createdAt != null && createdAt > 0) {
                    String since = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(createdAt);
                    tvMemberSince.setText("🗓️  Member since " + since);
                    tvMemberSince.setVisibility(android.view.View.VISIBLE);
                } else {
                    tvMemberSince.setVisibility(android.view.View.GONE);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        dbRef.child("items").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                int lost = 0, found = 0;
                for (DataSnapshot s : snap.getChildren()) {
                    if (uid.equals(s.child("userId").getValue(String.class))) {
                        if ("lost".equals(s.child("type").getValue(String.class))) lost++;
                        else found++;
                    }
                }
                tvLostCount.setText(String.valueOf(lost));
                tvFoundCount.setText(String.valueOf(found));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        dbRef.child("claims").orderByChild("claimantId").equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        tvClaimCount.setText(String.valueOf(snap.getChildrenCount()));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    @Override
    protected void onResume() { super.onResume(); loadProfile(); }
}
