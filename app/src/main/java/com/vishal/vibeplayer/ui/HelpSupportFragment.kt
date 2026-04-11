package com.vishal.vibeplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class HelpSupportFragment : Fragment(R.layout.fragment_help_support) {

    // IMPORTANT: Change this to your actual email address!
    private val supportEmail = "support@vibeplayer.com"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnHelpBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // Route to FAQs
        view.findViewById<View>(R.id.rowFaqs).setOnClickListener {
            val bundle = Bundle().apply { putString("PAGE_TITLE", "FAQs") }
            findNavController().navigate(R.id.appInfoFragment, bundle)
        }

        // Route to Terms
        view.findViewById<View>(R.id.rowTerms).setOnClickListener {
            val bundle = Bundle().apply { putString("PAGE_TITLE", "Terms & Privacy Policy") }
            findNavController().navigate(R.id.appInfoFragment, bundle)
        }

        // Open Email for Contact Us
        view.findViewById<View>(R.id.rowContactUs).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
                putExtra(Intent.EXTRA_SUBJECT, "VibePlayer Support Request")
            }
            startActivity(Intent.createChooser(intent, "Send Email via..."))
        }

        // Open Email for Bug Report (Auto-fills device details)
        view.findViewById<View>(R.id.rowReportIssue).setOnClickListener {
            val deviceInfo = "Device: ${android.os.Build.MODEL}\nOS: Android ${android.os.Build.VERSION.RELEASE}\n\nPlease describe the issue below:\n"

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
                putExtra(Intent.EXTRA_SUBJECT, "VibePlayer Bug Report")
                putExtra(Intent.EXTRA_TEXT, deviceInfo)
            }
            startActivity(Intent.createChooser(intent, "Report Issue via..."))
        }
    }
}