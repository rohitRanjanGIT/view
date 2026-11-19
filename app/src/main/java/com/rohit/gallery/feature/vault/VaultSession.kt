package com.rohit.gallery.feature.vault

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether Locked is open right now. Starts locked, and locks again whenever the Locked screen is left or hidden. */
@Singleton
class VaultSession @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}

sealed interface VaultAuthResult {
    data object Success : VaultAuthResult

    /** The phone has no screen lock (or fingerprint / face) set up, so Locked can't be protected. */
    data object NoScreenLock : VaultAuthResult

    data class Failed(val message: String) : VaultAuthResult
}

private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/**
 * Android's own unlock prompt: fingerprint or face, with the phone's PIN, pattern or password as the fallback. No
 * separate app PIN, so there is nothing extra to forget.
 */
fun promptVaultUnlock(activity: Activity, onResult: (VaultAuthResult) -> Unit) {
    val manager = activity.getSystemService(BiometricManager::class.java)
    if (manager == null || manager.canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(VaultAuthResult.NoScreenLock)
        return
    }
    BiometricPrompt.Builder(activity)
        .setTitle("Open Locked")
        .setSubtitle("Your locked photos and videos")
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
        .authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(VaultAuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(VaultAuthResult.Failed(errString.toString()))
                }
            },
        )
}

/**
 * While composed, the window can't be captured: no screenshots or screen recordings, and the recent-apps preview is
 * blank, so locked photos never show outside the app.
 */
@Composable
fun SecureWindow() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
