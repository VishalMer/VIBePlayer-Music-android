package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class AppInfoFragment : Fragment(R.layout.fragment_app_info) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnInfoBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // Grab the title passed from the previous screen
        val title = arguments?.getString("PAGE_TITLE") ?: "Information"
        view.findViewById<TextView>(R.id.txtInfoTitle).text = title

        val txtContent = view.findViewById<TextView>(R.id.txtInfoContent)

        // Dynamically change the text based on the title!
        if (title == "FAQs") {
            txtContent.text = """
                Q: How do I play music offline?
                A: Toggle 'Offline Mode' in settings!
                
                Q: Where are my downloaded songs?
                A: Check the 'My Library' section on the Home screen.
                
                Q: How do I create a custom mix?
                A: Go to the Playlists tab and click '+ Create'.
                
                Q: How does VibePlayer use my data?
                A: All custom playlists and history are stored locally on your device.
            """.trimIndent()
        }  else if (title == "Open Source Licenses") {
        // NEW SECTION: Added for the About App page
        txtContent.text = """
                ViBe Player uses the following open source libraries:
                
                • Glide v4.x
                  Copyright 2014 Google LLC.
                  Licensed under the BSD, part MIT and Apache 2.0 licenses.
                  
                • Retrofit v2.x
                  Copyright 2013 Square, Inc.
                  Licensed under the Apache License, Version 2.0.
                  
                • Room Persistence Library
                  Copyright The Android Open Source Project.
                  Licensed under the Apache License, Version 2.0.
                  
                • Kotlin Coroutines
                  Copyright JetBrains s.r.o.
                  Licensed under the Apache License, Version 2.0.
            """.trimIndent()
    } else {
            txtContent.text = """
                Terms of Service
                Last Updated: April 2026
                
                By using VibePlayer, you agree to these terms. VibePlayer is provided "as is" without warranty of any kind.
                
                Privacy Policy
                We respect your privacy. VibePlayer processes your local music files securely on your device. We do not sell your personal listening history to third parties. Online tracks are fetched via public APIs, subject to their respective terms.
            """.trimIndent()
        }
    }
}