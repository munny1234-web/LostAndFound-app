package com.example.lostandfound;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
public class ForgotPasswordActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        EditText etEmail = findViewById(R.id.etEmail);
        Button btnResetPassword = findViewById(R.id.btnResetPassword);
        Button btnBack = findViewById(R.id.btnBack);
        btnResetPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) { etEmail.setError("Enter email!"); return; }
            FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                Toast.makeText(this, task.isSuccessful() ? "Reset link sent!" : "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                if (task.isSuccessful()) finish();
            });
        });
        btnBack.setOnClickListener(v -> finish());
    }
}
