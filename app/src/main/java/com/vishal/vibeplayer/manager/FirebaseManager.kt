package com.vishal.vibeplayer.manager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    // This dynamically gets the ID of whoever is currently logged in!
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    // A helper to check if we should show the Login screen or Home screen
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}