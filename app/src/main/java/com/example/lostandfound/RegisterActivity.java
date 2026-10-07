package com.example.lostandfound;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;
public class RegisterActivity extends AppCompatActivity {
    EditText etName, etEmail, etPhone, etPassword;
    Button btnRegister;
    TextView tvLogin;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        etName = findViewById(R.id.etName); etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone); etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister); tvLogin = findViewById(R.id.tvLogin);
        btnRegister.setOnClickListener(v -> {
            String name = etName.getText().toString().trim(), email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim(), pass = etPassword.getText().toString().trim();
            if (name.isEmpty()) { etName.setError("Required!"); return; }
            if (email.isEmpty()) { etEmail.setError("Required!"); return; }
            if (pass.length() < 6) { etPassword.setError("Min 6 characters!"); return; }
            btnRegister.setEnabled(false); btnRegister.setText("Creating...");
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    Map<String,Object> user = new HashMap<>();
                    user.put("name",name); user.put("email",email); user.put("phone",phone);
                    FirebaseDatabase.getInstance().getReference().child("users").child(uid).setValue(user)
                        .addOnCompleteListener(t -> { startActivity(new Intent(this, HomeActivity.class)); finish(); });
                } else { Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_LONG).show(); btnRegister.setEnabled(true); btnRegister.setText("Create Account"); }
            });
        });
        tvLogin.setOnClickListener(v -> finish());
    }
}
