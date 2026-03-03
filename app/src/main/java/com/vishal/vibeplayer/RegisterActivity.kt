package com.vishal.vibeplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.vishal.vibeplayer.manager.FirebaseManager

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmailReg)
        val etMobile = findViewById<TextInputEditText>(R.id.etMobile)
        val etPassword = findViewById<TextInputEditText>(R.id.etPasswordReg)

        val btnRegister = findViewById<View>(R.id.btnRegister)
        val txtLoginNow = findViewById<TextView>(R.id.txtLoginNow)

        // 1. Handle "Sign In" link click
        txtLoginNow.setOnClickListener {
            // We will create LoginActivity in the next step!
            finish()
        }

        // 2. Handle "Sign Up" button click
        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val mobile = etMobile.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Basic Validation
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start the loading state
            btnRegister.isEnabled = false

            // Create the user in Firebase Auth
            FirebaseManager.auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = FirebaseManager.getCurrentUserId()

                        // Create a map of their profile data
                        val userProfile = hashMapOf(
                            "uid" to userId,
                            "fullName" to name,
                            "email" to email,
                            "mobile" to mobile,
                            "createdAt" to System.currentTimeMillis()
                        )

                        // Save profile to Firestore Database
                        if (userId != null) {
                            FirebaseManager.db.collection("users").document(userId)
                                .set(userProfile)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Account Created Successfully!", Toast.LENGTH_SHORT).show()
                                    // Send them to the main app!
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finishAffinity() // Closes all auth screens
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Database Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    btnRegister.isEnabled = true
                                }
                        }
                    } else {
                        // Registration failed (e.g., email already exists)
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        btnRegister.isEnabled = true
                    }
                }
        }
    }
}