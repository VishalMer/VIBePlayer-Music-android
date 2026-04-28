package com.vishal.vibeplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.vishal.vibeplayer.LoginActivity
import com.vishal.vibeplayer.R

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Back Button
        view.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // 2. Edit Profile Routing
        view.findViewById<View>(R.id.rowEditProfile).setOnClickListener {
            findNavController().navigate(R.id.action_appSettingsFragment_to_editProfileFragment)
        }

        // 3. Help & Support Routing
        view.findViewById<View>(R.id.rowHelp).setOnClickListener {
            findNavController().navigate(R.id.action_appSettingsFragment_to_helpSupportFragment)
        }

        // 4. About App Routing
        view.findViewById<View>(R.id.rowAbout).setOnClickListener {
            findNavController().navigate(R.id.action_appSettingsFragment_to_aboutAppFragment)
        }

        // 5. Firebase Logout Logic
        view.findViewById<View>(R.id.rowLogout).setOnClickListener {
            // Destroy the Firebase Session
            FirebaseAuth.getInstance().signOut()

            // Teleport to Login Screen and completely clear the app history (The Magic Lock)
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // --- COMING SOON FEATURES ---

        // 1. Notifications
        view.findViewById<View>(R.id.switchNotifications)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Notifications feature coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }

        // 2. Light Mode
        view.findViewById<View>(R.id.switchDarkTheme)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Light Mode is currently in development!", android.widget.Toast.LENGTH_SHORT).show()
        }

        // 3. Language
        view.findViewById<View>(R.id.rowLanguage)?.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "More languages coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}