package com.antigravity.locationtracker.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.util.AppLogger
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Manages Google Sign-In authentication state.
 */
@Suppress("DEPRECATION")
class GoogleAuthManager(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    
    companion object {
        private const val TAG = "GoogleAuthManager"
    }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    init {
        logDeviceInfo()
    }
    
    private fun logDeviceInfo() {
        AppLogger.i(TAG, "=== Device Info ===")
        AppLogger.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        AppLogger.i(TAG, "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        AppLogger.i(TAG, "Package: ${context.packageName}")
        
        // Log signing certificate SHA-1
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA-1")
                val sha1 = md.digest(signature.toByteArray())
                val sha1Hex = sha1.joinToString(":") { "%02X".format(it) }
                AppLogger.i(TAG, "APK SHA-1: $sha1Hex")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get signing info", e)
        }
        
        AppLogger.i(TAG, "Required scopes: ${SheetsRepository.REQUIRED_SCOPES}")
    }
    
    private val signInClient: GoogleSignInClient by lazy {
        AppLogger.d(TAG, "Creating GoogleSignInClient...")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SheetsRepository.REQUIRED_SCOPES[0]),
                Scope(SheetsRepository.REQUIRED_SCOPES[1])
            )
            .build()
        
        AppLogger.d(TAG, "GoogleSignInOptions built with scopes")
        GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Check if user is already signed in (silent sign-in).
     */
    suspend fun checkExistingSignIn() {
        AppLogger.i(TAG, "Checking existing sign-in...")
        _authState.value = AuthState.Loading
        
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            AppLogger.d(TAG, "Last signed-in account: ${account?.email ?: "null"}")
            
            if (account != null && hasRequiredScopes(account)) {
                AppLogger.i(TAG, "User already signed in with required scopes")
                saveUserInfo(account)
                _authState.value = AuthState.SignedIn(
                    email = account.email ?: "",
                    displayName = account.displayName ?: "",
                    photoUrl = account.photoUrl?.toString()
                )
            } else {
                AppLogger.d(TAG, "Attempting silent sign-in...")
                try {
                    val result = signInClient.silentSignIn().await()
                    AppLogger.i(TAG, "Silent sign-in successful: ${result.email}")
                    if (hasRequiredScopes(result)) {
                        saveUserInfo(result)
                        _authState.value = AuthState.SignedIn(
                            email = result.email ?: "",
                            displayName = result.displayName ?: "",
                            photoUrl = result.photoUrl?.toString()
                        )
                    } else {
                        AppLogger.w(TAG, "Silent sign-in missing required scopes")
                        _authState.value = AuthState.SignedOut
                    }
                } catch (e: ApiException) {
                    AppLogger.w(TAG, "Silent sign-in failed: code=${e.statusCode}, message=${e.message}")
                    _authState.value = AuthState.SignedOut
                }
            }
        } catch (e: ApiException) {
            AppLogger.e(TAG, "Check existing sign-in failed: code=${e.statusCode}", e)
            _authState.value = AuthState.SignedOut
        } catch (e: Exception) {
            AppLogger.e(TAG, "Check existing sign-in error", e)
            _authState.value = AuthState.Error(e.message ?: "Unknown error")
        }
    }
    
    private fun hasRequiredScopes(account: GoogleSignInAccount): Boolean {
        val hasAll = SheetsRepository.REQUIRED_SCOPES.all { scope ->
            GoogleSignIn.hasPermissions(account, Scope(scope))
        }
        AppLogger.d(TAG, "Has required scopes: $hasAll")
        return hasAll
    }
    
    /**
     * Get the intent to start the sign-in flow.
     */
    fun getSignInIntent(): Intent {
        AppLogger.i(TAG, "Getting sign-in intent...")
        return signInClient.signInIntent
    }
    
    /**
     * Handle the result from the sign-in activity.
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        AppLogger.i(TAG, "Handling sign-in result...")
        AppLogger.d(TAG, "Intent data: ${data?.extras?.keySet()?.toList()}")
        
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            AppLogger.d(TAG, "Task successful: ${task.isSuccessful}, complete: ${task.isComplete}")
            
            val account = task.getResult(ApiException::class.java)
            AppLogger.i(TAG, "Sign-in successful! Email: ${account.email}")
            AppLogger.d(TAG, "Account ID: ${account.id}")
            AppLogger.d(TAG, "Display name: ${account.displayName}")
            AppLogger.d(TAG, "Granted scopes: ${account.grantedScopes}")
            
            if (hasRequiredScopes(account)) {
                saveUserInfo(account)
                _authState.value = AuthState.SignedIn(
                    email = account.email ?: "",
                    displayName = account.displayName ?: "",
                    photoUrl = account.photoUrl?.toString()
                )
                Result.success(account)
            } else {
                AppLogger.e(TAG, "Required permissions not granted")
                _authState.value = AuthState.Error("Required permissions not granted")
                Result.failure(Exception("Required permissions not granted"))
            }
        } catch (e: ApiException) {
            val errorMessage = getSignInErrorMessage(e.statusCode)
            AppLogger.e(TAG, "Sign-in failed! Code: ${e.statusCode}, Message: $errorMessage", e)
            _authState.value = AuthState.Error("Sign-in failed: ${e.statusCode}")
            Result.failure(e)
        }
    }
    
    private fun getSignInErrorMessage(code: Int): String {
        return when (code) {
            4 -> "SIGN_IN_REQUIRED - User needs to sign in"
            7 -> "NETWORK_ERROR - Network unavailable"
            8 -> "INTERNAL_ERROR - Internal error"
            10 -> "DEVELOPER_ERROR - SHA-1 fingerprint mismatch! Check OAuth client config in Google Cloud Console"
            12500 -> "SIGN_IN_CANCELLED - User cancelled"
            12501 -> "SIGN_IN_CURRENTLY_IN_PROGRESS"
            12502 -> "SIGN_IN_FAILED"
            else -> "Unknown error code: $code"
        }
    }
    
    private fun saveUserInfo(account: GoogleSignInAccount) {
        AppLogger.d(TAG, "Saving user info...")
        securePrefs.userEmail = account.email
        securePrefs.userName = account.displayName
    }
    
    /**
     * Sign out the current user.
     */
    suspend fun signOut() {
        AppLogger.i(TAG, "Signing out...")
        try {
            signInClient.signOut().await()
            securePrefs.clearAuthData()
            _authState.value = AuthState.SignedOut
            AppLogger.i(TAG, "Sign out successful")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Sign out failed", e)
            _authState.value = AuthState.Error(e.message ?: "Sign-out failed")
        }
    }
    
    /**
     * Get the current signed-in account.
     */
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
}

/**
 * Represents the authentication state.
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object SignedOut : AuthState()
    data class SignedIn(
        val email: String,
        val displayName: String,
        val photoUrl: String?
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
