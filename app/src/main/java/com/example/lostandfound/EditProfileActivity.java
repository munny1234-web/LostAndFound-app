package com.example.lostandfound;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.*;
import java.util.*;

public class EditProfileActivity extends AppCompatActivity {

    EditText etName, etPhone;
    Button btnSave, btnBack, btnChangePhoto;
    ImageView imgProfile;
    Uri selectedImageUri = null;
    private static final int PICK_IMAGE = 101;
    String currentUserId;
    DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef = FirebaseDatabase.getInstance().getReference();

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        imgProfile = findViewById(R.id.imgProfile);

        btnBack.setOnClickListener(v -> finish());

        btnChangePhoto.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
            startActivityForResult(i, PICK_IMAGE);
        });

        loadProfile();
        btnSave.setOnClickListener(v -> saveProfile());
    }

    void loadProfile() {
        dbRef.child("users").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot snap) {
                        etName.setText(snap.child("name").getValue(String.class));
                        etPhone.setText(snap.child("phone").getValue(String.class));
                        String photoUrl = snap.child("photoUrl").getValue(String.class);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ImageUtils.loadProfileImage(EditProfileActivity.this, photoUrl, imgProfile);
                        }
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            imgProfile.setImageURI(selectedImageUri);
        }
    }

    void saveProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        if (name.isEmpty()) { etName.setError("Name required!"); return; }

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        if (selectedImageUri != null) {
            String base64Image = ImageUtils.encodeImageToBase64(this, selectedImageUri, 500, 55);
            if (base64Image != null) {
                updateProfile(name, phone, base64Image);
            } else {
                Toast.makeText(this, "Couldn't process the photo, saving profile without it.", Toast.LENGTH_LONG).show();
                updateProfile(name, phone, null);
            }
        } else {
            updateProfile(name, phone, null);
        }
    }

    void updateProfile(String name, String phone, String photoUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        if (photoUrl != null && !photoUrl.isEmpty()) updates.put("photoUrl", photoUrl);

        dbRef.child("users").child(currentUserId).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Error updating!", Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Changes");
                    }
                });
    }
}
