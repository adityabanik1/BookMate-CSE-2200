package com.example.BookMate;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.button.MaterialButton;

/**
 * LoginActivity.java — Login Screen (Launcher)
 * Week 8 : Explicit Intent to MainActivity after login.
 * Firebase: Authentication with Email/Password.
 *          - Login          → signInWithEmailAndPassword()
 *          - Create account → createUserWithEmailAndPassword()
 *          - If a user is already signed in, skip this screen
 *            and go straight to MainActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // UI References
    private EditText etEmail, etPassword;
    private MaterialButton btnLogin, btnCreateAccount;
    private ProgressBar progressLogin;

    // Firebase
    private FirebaseAuth auth;
    // Lifecycle — Week 8

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // Already signed in? Skip the login screen
        if (auth.getCurrentUser() != null) {
            openMainActivity();
            return;
        }

        setContentView(R.layout.activity_login);

        // Initialize UI references
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        progressLogin = findViewById(R.id.progressLogin);

        // Button listeners
        btnLogin.setOnClickListener(v -> login());
        btnCreateAccount.setOnClickListener(v -> createAccount());
    }
    // Firebase Authentication

    private void login() {
        if (!isInputValid()) return;

        setLoading(true);
        auth.signInWithEmailAndPassword(getEmail(), getPassword())
                .addOnCompleteListener(this, task -> handleAuthResult(task, "Logged in"));
    }

    private void createAccount() {
        if (!isInputValid()) return;

        setLoading(true);
        auth.createUserWithEmailAndPassword(getEmail(), getPassword())
                .addOnCompleteListener(this, task -> handleAuthResult(task, "Account created"));
    }

    /**
     * Called when Firebase finishes logging in or creating the account.
     * On success → open MainActivity. On failure → show Firebase's error message.
     */
    private void handleAuthResult(Task<AuthResult> task, String successMessage) {
        setLoading(false);
        if (task.isSuccessful()) {
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            openMainActivity();
        } else {
            String error = (task.getException() != null)
                    ? task.getException().getMessage()
                    : "Something went wrong";
            Log.w(TAG, "handleAuthResult: Failed", task.getException());
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
    }
    // Helpers

    private String getEmail() {
        return etEmail.getText().toString().trim();
    }

    private String getPassword() {
        return etPassword.getText().toString();
    }

    /**
     * Basic checks before calling Firebase (it would reject these anyway,
     * but this gives clearer messages).
     */
    private boolean isInputValid() {
        if (getEmail().isEmpty()) {
            etEmail.setError("Enter your email");
            return false;
        }
        if (getPassword().length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return false;
        }
        return true;
    }

    /** Shows the spinner and disables the buttons while Firebase is working. */
    private void setLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnCreateAccount.setEnabled(!loading);
    }

    /** Week 8: Explicit Intent to MainActivity, and close the login screen. */
    private void openMainActivity() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish(); // so Back from MainActivity doesn't return here
    }
}
