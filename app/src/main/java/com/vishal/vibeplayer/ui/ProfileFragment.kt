package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class ProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // 1. Find the buttons using their XML IDs
        // IMPORTANT: Ensure these IDs match your fragment_profile.xml exactly!
        val btnSettings = view.findViewById<View>(R.id.btnSettings)
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)

        // 2. Tell the Settings Gear to open the Settings screen
        btnSettings?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_appSettingsFragment)
        }

        // 3. Tell the Edit Profile button to open the Edit Profile screen
        btnEditProfile?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        return view
    }
}