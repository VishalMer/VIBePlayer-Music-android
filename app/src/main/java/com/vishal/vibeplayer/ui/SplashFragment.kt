package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class SplashFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wait for 2 seconds (2000 milliseconds) then jump to Home Screen
        Handler(Looper.getMainLooper()).postDelayed({
            // Check if we are still on the Splash Screen before navigating to prevent crashes
            if (findNavController().currentDestination?.id == R.id.splashFragment) {
                findNavController().navigate(R.id.action_splashFragment_to_homeFragment)
            }
        }, 2000)
    }
}