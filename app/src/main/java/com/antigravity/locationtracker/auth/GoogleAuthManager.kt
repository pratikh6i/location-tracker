package com.antigravity.locationtracker.auth

import android.content.Context
import android.content.Intent
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
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

/**
 * Manages Google Sign-In authentication state.
 */
class GoogleAuthManager(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SheetsRepository.REQUIRED_SCOPES[0]),
                Scope(SheetsRepository.REQUIRED_SCOPES[1])
            )
            .build()
        
        GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Check if user is already signed in (silent sign-in).
     */
    suspend fun checkExistingSignIn() {
        _authState.value = AuthState.Loading
        
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            
            if (account != null && hasRequiredScopes(account)) {
                // User is signed in with required scopes
                saveUserInfo(account)
                _authState.value = AuthState.SignedIn(
                    email = account.email ?: "",
                    displayName = account.displayName ?: "",
                    photoUrl = account.photoUrl?.toString()
                )
            } else {
                // Try silent sign-in
                val result = signInClient.silentSignIn().await()
                if (hasRequiredScopes(result)) {
                    saveUserInfo(result)
                    _authState.value = AuthState.SignedIn(
                        email = result.email ?: "",
                        displayName = result.displayName ?: "",
                        photoUrl = result.photoUrl?.toString()
                    )
                } else {
                    _authState.value = AuthState.SignedOut
                }
            }
        } catch (e: ApiException) {
            _authState.value = AuthState.SignedOut
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Unknown error")
        }
    }
    
    private fun hasRequiredScopes(account: GoogleSignInAccount): Boolean {
        return SheetsRepository.REQUIRED_SCOPES.all { scope ->
            GoogleSignIn.hasPermissions(account, Scope(scope))
        }
    }
    
    /**
     * Get the intent to start the sign-in flow.
     */
    fun getSignInIntent(): Intent = signInClient.signInIntent
    
    /**
     * Handle the result from the sign-in activity.
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (hasRequiredScopes(account)) {
                saveUserInfo(account)
                _authState.value = AuthState.SignedIn(
                    email = account.email ?: "",
                    displayName = account.displayName ?: "",
                    photoUrl = account.photoUrl?.toString()
                )
                Result.success(account)
            } else {
                _authState.value = AuthState.Error("Required permissions not granted")
                Result.failure(Exception("Required permissions not granted"))
            }
        } catch (e: ApiException) {
            _authState.value = AuthState.Error("Sign-in failed: ${e.statusCode}")
            Result.failure(e)
        }
    }
    
    private fun saveUserInfo(account: GoogleSignInAccount) {
        securePrefs.userEmail = account.email
        securePrefs.userName = account.displayName
    }
    
    /**
     * Sign out the current user.
     */
    suspend fun signOut() {
        try {
            signInClient.signOut().await()
            securePrefs.clearAuthData()
            _authState.value = AuthState.SignedOut
        } catch (e: Exception) {
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
