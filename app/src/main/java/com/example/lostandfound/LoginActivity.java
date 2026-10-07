package com.example.lostandfound;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView tvRegister, tvForgotPassword, tvTogglePassword;
    FirebaseAuth auth;
    boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvTogglePassword = findViewById(R.id.tvTogglePassword);

        tvTogglePassword.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            int cursor = etPassword.getSelectionStart();
            if (passwordVisible) {
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                tvTogglePassword.setText("🙈");
            } else {
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                tvTogglePassword.setText("👁");
            }
            if (cursor >= 0) etPassword.setSelection(cursor);
        });

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();
            if (email.isEmpty()) { etEmail.setError("Enter email!"); return; }
            if (pass.isEmpty()) { etPassword.setError("Enter password!"); return; }
            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in...");
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                } else {
                    String reason = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(this, "Login failed: " + reason, Toast.LENGTH_LONG).show();
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Sign In");
                }
            });
        });
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        // Admin uses the same login as everyone else — after signing in, the
        // Admin Dashboard appears inside the Profile screen (admin account only).
    }
}
