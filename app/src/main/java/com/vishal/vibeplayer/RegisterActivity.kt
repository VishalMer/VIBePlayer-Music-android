package com.vishal.vibeplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmailReg)
        val etMobile = findViewById<TextInputEditText>(R.id.etMobile)
        val etPassword = findViewById<TextInputEditText>(R.id.etPasswordReg)

        val btnRegister = findViewById<View>(R.id.btnRegister)
        val txtLoginNow = findViewById<TextView>(R.id.txtLoginNow)

        txtLoginNow.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val mobile = etMobile.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val userId = user?.uid

                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        user?.updateProfile(profileUpdates)

                        val userProfile = hashMapOf(
                            "name" to name,
                            "email" to email,
                            "mobile" to mobile,
                            "username" to "",
                            "bio" to "",
                            "createdAt" to System.currentTimeMillis()
                        )

                        // 1. Save to Database in the background
                        if (userId != null) {
                            FirebaseDatabase.getInstance().getReference("users").child(userId)
                                .setValue(userProfile)
                        }

                        // 2. UNSTOPPABLE REDIRECT: Because Auth succeeded, we immediately
                        // send them to the main app! No more freezing.
                        Toast.makeText(this@RegisterActivity, "Account Created!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finishAffinity()

                    } else {
                        Toast.makeText(this@RegisterActivity, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        btnRegister.isEnabled = true
                    }
                }
        }
    }
}