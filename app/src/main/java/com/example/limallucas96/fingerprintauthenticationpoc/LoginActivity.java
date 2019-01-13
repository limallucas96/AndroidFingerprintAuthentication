package com.example.limallucas96.fingerprintauthenticationpoc;

import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.example.limallucas96.fingerprintauthenticationpoc.Security.CryptoHelper;
import com.example.limallucas96.fingerprintauthenticationpoc.Security.SharedPreferencesHelper;
import com.example.limallucas96.fingerprintauthenticationpoc.dialog.FingerprintDialogFragment;

import javax.crypto.Cipher;

import static android.Manifest.permission.USE_FINGERPRINT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class LoginActivity extends AppCompatActivity {

    private static final String FINGERPRINT_DIALOG_TAG = "FINGERPRINT_DIALOG_TAG";
    static final int FINGERPRINT_AUTH_STAGE_ENROLLMENT = 1;
    static final int FINGERPRINT_AUTH_STAGE_AUTHENTICATION = 2;

    private CryptoHelper mCryptoHelper;
    private SharedPreferencesHelper mSharedPreferencesHelper;
    private FingerprintManager mFingerprintManager;
    private CancellationSignal mCancellationSignal;
    private FingerprintDialogFragment mFingerprintDialogFragment;

    private EditText mUsername;
    private EditText mPassword;
    private Button mDefaultAuthentication;
    private Button mFingerprintAuthentication;
    private CheckBox mFingerPrintSignOn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setupActivity();
        mFingerprintManager = getSystemService(FingerprintManager.class);

        if (mFingerPrintSignOn.isChecked()) {
            performFingerprintAuthentication();
        } else {
            performRegularAuthentication();
        }
    }

    private void setupActivity() {
        mUsername = findViewById(R.id.username);
        mPassword = findViewById(R.id.password);
        mDefaultAuthentication = findViewById(R.id.default_authentication);
        mFingerprintAuthentication = findViewById(R.id.fingerprint_authentication);
        mFingerPrintSignOn = findViewById(R.id.fingerprintSignon);

        mFingerPrintSignOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mSharedPreferencesHelper.setEnableFingerprintAuth(b);
            }
        });
        mFingerPrintSignOn.setChecked(mSharedPreferencesHelper.isFingerprintAuthEnabled());
    }

    private void startHomeActivity() {
        startActivity(new Intent(this, HomeActivity.class));
    }

    private void performFingerprintAuthentication() {
        if (ActivityCompat.checkSelfPermission(this, USE_FINGERPRINT) != PERMISSION_GRANTED) {
            return;
        }
        if (!mFingerprintManager.isHardwareDetected() || !mFingerprintManager.hasEnrolledFingerprints()) {
            Toast.makeText(getApplicationContext(), "Fingerprint Unavailable. Please configure a fingerprint", Toast.LENGTH_SHORT).show();
            return;
        }
        mFingerprintDialogFragment = FingerprintDialogFragment.newInstance(getStage());
        mFingerprintDialogFragment.show(getFragmentManager(), FINGERPRINT_DIALOG_TAG);
        FingerprintManager.CryptoObject cryptoObject = configureCryptoObject(getStage());
        mCancellationSignal = new CancellationSignal();
        mFingerprintManager.authenticate(cryptoObject, mCancellationSignal, 0, new FingerprintManager.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                mFingerprintDialogFragment.dismiss();
                if (getStage() == FINGERPRINT_AUTH_STAGE_ENROLLMENT) {
                    mCryptoHelper.encrypt(result.getCryptoObject(), "hypothetical decrypted token");
                    Toast.makeText(getApplicationContext(), "Fingerprint Registered.", Toast.LENGTH_SHORT).show();
                    mSharedPreferencesHelper.setEnableFingerprintAuth(true);
                    mSharedPreferencesHelper.setIsEnrolledInFingerprintAuth();
                    performFingerprintAuthentication();
                } else {
                    String decryptedToken = mCryptoHelper.decrypt(result.getCryptoObject());
                    Toast.makeText(getApplicationContext(),
                            "Fingerprint Registered. Token decrypted: " + decryptedToken,
                            Toast.LENGTH_SHORT).show();
                    Toast.makeText(getApplicationContext(), "Token received: %s" + decryptedToken, Toast.LENGTH_SHORT).show();
                    startHomeActivity();
                    finish();
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                //other situations can be handled here like the sensor didnt have a chance
                // to read the image or the operation timed out
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Unrecognized Fingerprint", Toast.LENGTH_SHORT).show();
            }
        }, null);
    }

    private int getStage() {
        if (mSharedPreferencesHelper.isEnrolledInFingerprintAuth() &&
                mSharedPreferencesHelper.isFingerprintAuthEnabled()) {
            return FINGERPRINT_AUTH_STAGE_AUTHENTICATION;
        }
        return FINGERPRINT_AUTH_STAGE_ENROLLMENT;
    }

    private FingerprintManager.CryptoObject configureCryptoObject(int stage) {
        if (stage == FINGERPRINT_AUTH_STAGE_ENROLLMENT) {
            return mCryptoHelper.createCryptoObject(Cipher.ENCRYPT_MODE);
        }
        return mCryptoHelper.createCryptoObject(Cipher.DECRYPT_MODE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFingerprintDialogFragment.dismiss();
        mCancellationSignal.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getStage() == FINGERPRINT_AUTH_STAGE_AUTHENTICATION) {
            performFingerprintAuthentication();
        }
    }

    private void performRegularAuthentication() {
        startHomeActivity();
    }

}

