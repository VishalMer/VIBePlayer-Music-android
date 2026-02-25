package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class EditProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        // Find your back button (Make sure R.id.btnEditBack matches your XML!)
        val btnBack = view.findViewById<View>(R.id.btnEditBack)

        // Go back!
        btnBack?.setOnClickListener {
            findNavController().navigateUp()
        }

        return view
    }
}