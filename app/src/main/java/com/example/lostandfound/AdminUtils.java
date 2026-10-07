package com.example.lostandfound;

import com.google.firebase.auth.FirebaseUser;

// Only this email is treated as the admin account. LoginActivity uses this to
// route after sign-in, and every Admin* screen checks it again on its own —
// so even if someone reached an Admin screen another way, they'd be bounced
// straight back out unless they're signed in as this exact account.
public class AdminUtils {
    public static final String ADMIN_EMAIL = "cse2310042@adust.edu.bd";

    public static boolean isAdmin(FirebaseUser user) {
        return user != null && user.getEmail() != null && ADMIN_EMAIL.equalsIgnoreCase(user.getEmail());
    }
}
