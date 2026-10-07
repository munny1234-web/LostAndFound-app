package com.example.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

// A dedicated login screen just for the admin account, kept separate from
// LoginActivity so regular users never see or use anything admin-related.
// Even if someone types the right credentials for a non-admin account here,
// AdminUtils.isAdmin() blocks entry into the Admin Panel.
public class AdminLoginActivity extends AppCompatActivity {

    EditText etAdminEmail, etAdminPassword;
    Button btnAdminLoginSubmit;
    TextView btnBackToUserLogin;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        auth = FirebaseAuth.getInstance();
        etAdminEmail = findViewById(R.id.etAdminEmail);
        etAdminPassword = findViewById(R.id.etAdminPassword);
        btnAdminLoginSubmit = findViewById(R.id.btnAdminLoginSubmit);
        btnBackToUserLogin = findViewById(R.id.btnBackToUserLogin);

        btnBackToUserLogin.setOnClickListener(v -> finish());

        btnAdminLoginSubmit.setOnClickListener(v -> {
            String email = etAdminEmail.getText().toString().trim();
            String pass = etAdminPassword.getText().toString().trim();
            if (email.isEmpty()) { etAdminEmail.setError("Enter email!"); return; }
            if (pass.isEmpty()) { etAdminPassword.setError("Enter password!"); return; }

            btnAdminLoginSubmit.setEnabled(false);
            btnAdminLoginSubmit.setText("Verifying...");

            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful() && AdminUtils.isAdmin(auth.getCurrentUser())) {
                    startActivity(new Intent(this, AdminActivity.class));
                    finish();
                } else {
                    // Wrong password, OR correct password but not the admin
                    // account — sign out either way so nothing stays half-logged-in.
                    auth.signOut();
                    Toast.makeText(this, "Access denied — not an admin account", Toast.LENGTH_LONG).show();
                    btnAdminLoginSubmit.setEnabled(true);
                    btnAdminLoginSubmit.setText("Enter Admin Panel");
                }
            });
        });
    }
}
