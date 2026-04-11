package com.vishal.vibeplayer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class AboutAppFragment : Fragment(R.layout.fragment_about_app) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back Button
        view.findViewById<View>(R.id.btnAboutBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // 1. Rate Us (Not on Play Store yet)
        view.findViewById<View>(R.id.rowRateUs).setOnClickListener {
            Toast.makeText(requireContext(), "Coming soon to the Play Store! Stay tuned.", Toast.LENGTH_SHORT).show()
        }

        // 2. Share App
        view.findViewById<View>(R.id.rowShareApp).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ViBe Player")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out ViBe Player! A sleek, offline-first music player with custom mixes and a modern UI. Coming soon to Android!"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share ViBe Player via..."))
        }

        // 3. Open Source Licenses (Reusing our AppInfoFragment!)
        view.findViewById<View>(R.id.rowLicenses).setOnClickListener {
            val bundle = Bundle().apply { putString("PAGE_TITLE", "Open Source Licenses") }
            findNavController().navigate(R.id.appInfoFragment, bundle)
        }
    }
}