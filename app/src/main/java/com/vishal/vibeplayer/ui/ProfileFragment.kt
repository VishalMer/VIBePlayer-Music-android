package com.vishal.vibeplayer.ui

import android.content.Context
import android.graphics.Bitmap
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileFragment : Fragment() {

    private var rootView: View? = null
    private var profileListener: ValueEventListener? = null
    private val dbRef by lazy {
        FirebaseAuth.getInstance().currentUser?.let {
            FirebaseDatabase.getInstance().getReference("users").child(it.uid)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_profile, container, false)
            setupViews(rootView!!)
            setupMostPlayedRecyclerView(rootView!!)
        }
        return rootView
    }

    override fun onResume() {
        super.onResume()
        if (PlayerManager.allSongs.isNotEmpty()) {
            loadMostPlayedTracks()
        }

        // 1. INSTANT LOAD: Read from Local Storage the millisecond the screen opens
        rootView?.let { loadLocalProfileData(it) }

        // 2. CLOUD SYNC: Listen to Firebase in the background
        rootView?.let { setupRealtimeProfileSync(it) }
    }

    override fun onPause() {
        super.onPause()
        profileListener?.let { dbRef?.removeEventListener(it) }
    }

    // ==========================================
    // ZERO-LAG LOCAL LOAD
    // ==========================================
    private fun loadLocalProfileData(view: View) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val prefs = requireContext().getSharedPreferences("VibeProfilePrefs", Context.MODE_PRIVATE)

        val txtUserName = view.findViewById<TextView>(R.id.txtUserName)
        val txtUserHandle = view.findViewById<TextView>(R.id.txtUserHandle)
        val txtUserSubtitle = view.findViewById<TextView>(R.id.txtUserSubtitle)

        // Read Local Storage, fallback to Firebase Auth, fallback to Defaults
        txtUserName.text = prefs.getString("name", user.displayName ?: "Music Lover")

        val localUsername = prefs.getString("username", "")
        if (!localUsername.isNullOrEmpty()) {
            txtUserHandle.text = if (localUsername.startsWith("@")) localUsername else "@$localUsername"
        } else {
            val fallbackHandle = user.email?.substringBefore("@") ?: "user"
            txtUserHandle.text = "@$fallbackHandle"
        }

        val localBio = prefs.getString("bio", "")
        if (!localBio.isNullOrEmpty()) {
            txtUserSubtitle.text = localBio
        } else {
            txtUserSubtitle.text = "Add a bio in Edit Profile"
        }
    }

    // ==========================================
    // FIREBASE CLOUD SYNC
    // ==========================================
    private fun setupRealtimeProfileSync(view: View) {
        val txtUserName = view.findViewById<TextView>(R.id.txtUserName)
        val txtUserHandle = view.findViewById<TextView>(R.id.txtUserHandle)
        val txtUserSubtitle = view.findViewById<TextView>(R.id.txtUserSubtitle)
        val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatar)
        val prefs = requireContext().getSharedPreferences("VibeProfilePrefs", Context.MODE_PRIVATE)

        profileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                // Pull from Firebase
                val cloudName = snapshot.child("name").getValue(String::class.java)
                val cloudUsername = snapshot.child("username").getValue(String::class.java)
                val cloudBio = snapshot.child("bio").getValue(String::class.java)

                // If Firebase has data, update the UI AND overwrite Local Storage (for cross-device sync)
                val editor = prefs.edit()

                if (!cloudName.isNullOrEmpty()) {
                    txtUserName.text = cloudName
                    editor.putString("name", cloudName)
                }

                if (!cloudUsername.isNullOrEmpty()) {
                    val formattedUsername = if (cloudUsername.startsWith("@")) cloudUsername else "@$cloudUsername"
                    txtUserHandle.text = formattedUsername
                    editor.putString("username", cloudUsername)
                }

                if (!cloudBio.isNullOrEmpty()) {
                    txtUserSubtitle.text = cloudBio
                    editor.putString("bio", cloudBio)
                }

                editor.apply() // Commit Firebase data to Local Storage

                // Handle Image Sync
                val localFile = File(requireContext().filesDir, "vibe_profile.jpg")
                val base64Image = snapshot.child("profileImage").getValue(String::class.java)

                if (base64Image != null) {
                    try {
                        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        Glide.with(this@ProfileFragment).load(bitmap).apply(RequestOptions.circleCropTransform()).placeholder(R.drawable.default_pp).error(R.drawable.default_pp).into(imgAvatar)

                        java.io.FileOutputStream(localFile).use { outStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                } else {
                    if (localFile.exists()) localFile.delete()
                    Glide.with(this@ProfileFragment).load(R.drawable.default_pp).apply(RequestOptions.circleCropTransform()).into(imgAvatar)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        dbRef?.addValueEventListener(profileListener!!)
    }

    private fun setupViews(view: View) {
        val btnSettings = view.findViewById<View>(R.id.btnSettings)
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)
        val cardMyLibrary = view.findViewById<View>(R.id.cardMyLibrary)
        val cardDownloads = view.findViewById<View>(R.id.cardDownloads)
        val cardHistory = view.findViewById<View>(R.id.cardHistory)
        val cardLikedSongs = view.findViewById<View>(R.id.cardLikedSongs)

        cardMyLibrary?.setOnClickListener { openSpecialPlaylist("My Library", -2) }
        cardHistory?.setOnClickListener { openSpecialPlaylist("Listening History", -3) }
        cardDownloads?.setOnClickListener { openSpecialPlaylist("Downloads", -4) }
        cardLikedSongs?.setOnClickListener { openSpecialPlaylist("Liked Songs", -1) }

        btnSettings?.setOnClickListener { findNavController().navigate(R.id.action_profileFragment_to_appSettingsFragment) }
        btnEditProfile?.setOnClickListener { findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment) }
    }

    private fun setupMostPlayedRecyclerView(view: View) {
        val rvMostPlayed = view.findViewById<RecyclerView>(R.id.rvMostPlayedProfile)
        rvMostPlayed?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMostPlayed?.setHasFixedSize(true)
        rvMostPlayed?.setItemViewCacheSize(20)
    }

    private fun loadMostPlayedTracks() {
        val rvMostPlayed = view?.findViewById<RecyclerView>(R.id.rvMostPlayedProfile)
        if (rvMostPlayed?.adapter != null) return

        viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            val mostPlayedList = withContext(Dispatchers.Default) {
                var list = PlayerManager.allSongs
                    .filter { PlayerManager.playCounts.containsKey(it.path ?: "") }
                    .sortedByDescending { PlayerManager.playCounts[it.path ?: ""] ?: 0 }
                    .take(15)

                if (list.isEmpty()) {
                    list = PlayerManager.allSongs.filter { !it.isOnline }.shuffled().take(15)
                }
                list
            }

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