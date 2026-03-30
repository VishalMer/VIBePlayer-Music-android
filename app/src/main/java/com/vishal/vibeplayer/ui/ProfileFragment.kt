package com.vishal.vibeplayer.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Bitmap

class ProfileFragment : Fragment() {

    // 1. View Caching (Prevents lag when switching back to the Profile tab!)
    private var rootView: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        // 2. ONLY inflate and setup buttons if the view hasn't been built yet
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_profile, container, false)

            setupViews(rootView!!)
            setupMostPlayedRecyclerView(rootView!!)
        }
        return rootView
    }

    override fun onResume() {
        super.onResume()
        // 3. Refresh the "Most Played" covers every time the user opens the profile
        if (PlayerManager.allSongs.isNotEmpty()) {
            loadMostPlayedTracks()
        }

        // 4. THE MAGIC: Fetch Profile Data from Cloud or Local Storage
        rootView?.let { loadCloudUserProfile(it) }
    }

    // ==========================================
    // CLOUD & OFFLINE SYNC ENGINE
    // ==========================================
    private fun loadCloudUserProfile(view: View) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val txtUserName = view.findViewById<TextView>(R.id.txtUserName)
            val txtUserHandle = view.findViewById<TextView>(R.id.txtUserHandle)
            val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatar)

            // 1. Set Auth Data
            if (!user.displayName.isNullOrEmpty()) {
                txtUserName.text = user.displayName
            }
            if (!user.email.isNullOrEmpty()) {
                val handle = user.email!!.substringBefore("@")
                txtUserHandle.text = "@$handle"
            }

            // 2. OFFLINE FIRST: Check local memory for the profile picture
            val localFile = File(requireContext().filesDir, "vibe_profile.jpg")

            if (localFile.exists()) {
                // If we have it saved on the phone, load it instantly!
                Glide.with(this)
                    .load(localFile)
                    .apply(RequestOptions.circleCropTransform())
                    // SMART CACHE: Uses RAM to prevent lag, but updates if the file gets modified!
                    .signature(ObjectKey(localFile.lastModified()))
                    .into(imgAvatar)
            } else {
                // 3. FALLBACK: Fetch from Firebase if there is no local file
                val dbRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid)
                dbRef.child("profileImage").get().addOnSuccessListener { snapshot ->
                    val base64Image = snapshot.getValue(String::class.java)

                    if (base64Image != null) {
                        try {
                            // Decode the giant text string back into image pixels
                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                            // Show it in the UI
                            Glide.with(this)
                                .load(bitmap)
                                .apply(RequestOptions.circleCropTransform())
                                .into(imgAvatar)

                            // Save it to phone memory so we don't have to fetch it next time!
                            java.io.FileOutputStream(localFile).use { outStream ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun setupViews(view: View) {
        // Find the buttons using your exact XML IDs
        val btnSettings = view.findViewById<View>(R.id.btnSettings)
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)

        val cardMyLibrary = view.findViewById<View>(R.id.cardMyLibrary)
        val cardDownloads = view.findViewById<View>(R.id.cardDownloads)
        val cardHistory = view.findViewById<View>(R.id.cardHistory)
        val cardLikedSongs = view.findViewById<View>(R.id.cardLikedSongs)

        // Set Click Listeners with Special Negative IDs
        cardMyLibrary?.setOnClickListener { openSpecialPlaylist("My Library", -2) }
        cardHistory?.setOnClickListener { openSpecialPlaylist("Listening History", -3) }
        cardDownloads?.setOnClickListener { openSpecialPlaylist("Downloads", -4) }
        cardLikedSongs?.setOnClickListener { openSpecialPlaylist("Liked Songs", -1) }

        // Settings and Edit Profile Navigation
        btnSettings?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_appSettingsFragment)
        }

        btnEditProfile?.setOnClickListener {
            // Replaced R.id.editProfileFragment with the action ID
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }

    private fun setupMostPlayedRecyclerView(view: View) {
        val rvMostPlayed = view.findViewById<RecyclerView>(R.id.rvMostPlayedProfile)

        rvMostPlayed?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMostPlayed?.setHasFixedSize(true)
        rvMostPlayed?.setItemViewCacheSize(20)
    }

    // ==========================================
    // ZERO-LAG ANALYTICS ENGINE
    // ==========================================
    private fun loadMostPlayedTracks() {
        val rvMostPlayed = view?.findViewById<RecyclerView>(R.id.rvMostPlayedProfile)

        // NEW LINE: Prevent lag! Don't rebuild the list if it's already showing.
        if (rvMostPlayed?.adapter != null) return

        viewLifecycleOwner.lifecycleScope.launch {

            // Wait 150ms for the bottom navigation pill to finish sliding!
            delay(150)

            // Do the heavy sorting math in the background
            val mostPlayedList = withContext(Dispatchers.Default) {
                var list = PlayerManager.allSongs
                    .filter { PlayerManager.playCounts.containsKey(it.path ?: "") }
                    .sortedByDescending { PlayerManager.playCounts[it.path ?: ""] ?: 0 }
                    .take(15)

                // Fallback to random if they haven't played anything yet
                if (list.isEmpty()) {
                    list = PlayerManager.allSongs.filter { !it.isOnline }.shuffled().take(15)
                }
                list
            }

            // Draw to the UI safely
            withContext(Dispatchers.Main) {
                rvMostPlayed?.adapter = SquareSongAdapter(mostPlayedList) { clickedSong ->
                    val index = mostPlayedList.indexOf(clickedSong)
                    PlayerManager.startPlaying(requireContext(), mostPlayedList, index)
                }
            }
        }
    }

    private fun openSpecialPlaylist(title: String, specialId: Int) {
        val bundle = Bundle().apply {
            putString("PLAYLIST_NAME", title)
            putInt("CUSTOM_PLAYLIST_ID", specialId)
        }
        findNavController().navigate(R.id.playlistDetailsFragment, bundle)
    }
}