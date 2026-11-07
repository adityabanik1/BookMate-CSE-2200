package com.example.BookMate;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * LoginActivity.java — Login Screen (Launcher)
 * Week 8 : Explicit Intent to MainActivity after login.
 * Firebase: Authentication with Email/Password & Google Sign-In.
 *          - Email Login     → signInWithEmailAndPassword()
 *          - Create account  → createUserWithEmailAndPassword()
 *          - Google Sign-In  → signInWithCredential(GoogleAuthProvider)
 *          - If a user is already signed in, skip this screen
 *            and go straight to MainActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // UI References
    private EditText etEmail, etPassword;
    private MaterialButton btnLogin, btnCreateAccount, btnGoogleSignIn;
    private ProgressBar progressLogin;

    // Firebase
    private FirebaseAuth auth;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
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

        // Register Activity Result launcher for Google Sign-In
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        setLoading(false);
                    }
                });

        setContentView(R.layout.activity_login);

        // Initialize UI references
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressLogin = findViewById(R.id.progressLogin);

        // Button listeners
        btnLogin.setOnClickListener(v -> login());
        btnCreateAccount.setOnClickListener(v -> createAccount());
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
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

    private void signInWithGoogle() {
        String webClientId = getWebClientId();
        if (webClientId == null || webClientId.trim().isEmpty()) {
            Toast.makeText(this,
                    "Google Sign-In is not configured yet. Please check your google-services.json or Firebase settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId.trim())
                .requestEmail()
                .build();

        GoogleSignInClient client = GoogleSignIn.getClient(this, gso);
        client.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = client.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                firebaseAuthWithGoogle(account.getIdToken());
            } else {
                setLoading(false);
                Toast.makeText(this, "Google sign-in was unsuccessful", Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            setLoading(false);
            Log.w(TAG, "Google sign-in failed: statusCode=" + e.getStatusCode(), e);
            if (e.getStatusCode() != 12501) { // User cancellation is quiet; other failures are shown.
                Toast.makeText(this, "Google sign-in failed (" + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> handleAuthResult(task, "Signed in with Google"));
    }

    /**
     * Resolves the Web Client ID required by GoogleAuthProvider.
     * Checks BuildConfig (from secrets.properties), generated default_web_client_id,
     * or custom string resource google_web_client_id.
     */
    private String getWebClientId() {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID != null && !BuildConfig.GOOGLE_WEB_CLIENT_ID.trim().isEmpty()) {
            return BuildConfig.GOOGLE_WEB_CLIENT_ID.trim();
        }
        int resId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (resId != 0) {
            String val = getString(resId);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        int customResId = getResources().getIdentifier("google_web_client_id", "string", getPackageName());
        if (customResId != 0) {
            String val = getString(customResId);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return null;
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
        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setEnabled(!loading);
        }
    }

    /** Week 8: Explicit Intent to MainActivity, and close the login screen. */
    private void openMainActivity() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish(); // so Back from MainActivity doesn't return here
    }
}
