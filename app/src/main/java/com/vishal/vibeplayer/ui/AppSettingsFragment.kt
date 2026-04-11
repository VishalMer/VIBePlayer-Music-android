package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class AppSettingsFragment : Fragment(R.layout.fragment_app_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Back Button
        view.findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // 2. Edit Profile Routing (Triggers your custom XML animation!)
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

        // 5. Logout Placeholder
        view.findViewById<View>(R.id.rowLogout).setOnClickListener {
            Toast.makeText(requireContext(), "Logout clicked", Toast.LENGTH_SHORT).show()
        }
    }
}