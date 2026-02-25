package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class AppSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_app_settings, container, false)

        // Find your back button (Make sure R.id.btnSettingsBack matches your XML!)
        val btnBack = view.findViewById<View>(R.id.btnSettingsBack)

        // Tell it to pop the current screen off the stack (go back)
        btnBack?.setOnClickListener {
            findNavController().navigateUp()
        }

        return view
    }
}