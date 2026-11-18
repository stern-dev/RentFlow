package com.example.data.firebase

import android.content.Context
import com.example.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

/**
 * Compatibility facade delegating Google Sign-In and session management to [UserRepository].
 */
object AuthManager {
    suspend fun attemptAutoSignIn(context: Context): Boolean =
        UserRepository(context).attemptAutoSignIn(context)

    suspend fun signInInteractive(context: Context): AuthSignInResult =
        UserRepository(context).signInWithGoogle(context)

    suspend fun signOut(context: Context) {
        UserRepository(context).signOut(context)
    }
}

sealed interface AuthSignInResult {
    data object Success : AuthSignInResult
    data object Cancelled : AuthSignInResult
    data class Error(val message: String) : AuthSignInResult
}
