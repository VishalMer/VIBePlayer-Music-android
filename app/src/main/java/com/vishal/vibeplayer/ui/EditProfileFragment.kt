package com.vishal.vibeplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.vishal.vibeplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var selectedImageUri: Uri? = null
    private lateinit var imgEditAvatar: ImageView
    private lateinit var etEditName: EditText

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            Glide.with(this).load(uri).apply(RequestOptions.circleCropTransform()).into(imgEditAvatar)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgEditAvatar = view.findViewById(R.id.imgAvatarInner)
        etEditName = view.findViewById(R.id.etEditName)
        val btnSave = view.findViewById<View>(R.id.btnSaveProfile)

        val currentUser = FirebaseAuth.getInstance().currentUser
        etEditName.setText(currentUser?.displayName ?: "")

        val btnEditBack = view.findViewById<View>(R.id.btnEditBack)
        btnEditBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // ==========================================
        // OFFLINE FIRST: Check local memory for the profile picture
        // ==========================================
        val localFile = File(requireContext().filesDir, "vibe_profile.jpg")

        if (localFile.exists()) {
            // Load the locally saved image instantly
            Glide.with(this)
                .load(localFile)
                .apply(RequestOptions.circleCropTransform())
                // SMART CACHE: Uses RAM to prevent lag, but updates if the file gets modified!
                .signature(ObjectKey(localFile.lastModified()))
                .into(imgEditAvatar)
        } else if (currentUser != null) {
            // FALLBACK: If no local file exists, fetch from Firebase
            val dbRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            dbRef.child("profileImage").get().addOnSuccessListener { snapshot ->
                val base64Image = snapshot.getValue(String::class.java)
                if (base64Image != null) {
                    try {
                        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        Glide.with(this)
                            .load(bitmap)
                            .apply(RequestOptions.circleCropTransform())
                            .into(imgEditAvatar)

                        // Save it locally so we don't have to fetch it next time
                        java.io.FileOutputStream(localFile).use { outStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        imgEditAvatar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSave.setOnClickListener {
            val newName = etEditName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSave.isEnabled = false // Disable it so they can't click twice

            saveProfileData(newName, btnSave)
        }
    }

    private fun saveProfileData(newName: String, saveButton: View) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid)

        // 1. Save the new name (Added Failure Listener to catch Firebase rule issues)
        dbRef.child("name").setValue(newName).addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to save name: ${it.message}", Toast.LENGTH_LONG).show()
        }

        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
        user.updateProfile(profileUpdates)

        if (selectedImageUri != null) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(selectedImageUri!!)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)

                    // SAFETY CHECK: If Android fails to decode the image, stop here!
                    if (originalBitmap == null) {
                        throw Exception("Android could not read this specific image file.")
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 256, 256, true)

                    // NEW: SAVE TO LOCAL DEVICE MEMORY
                    try {
                        val localFile = File(requireContext().filesDir, "vibe_profile.jpg")
                        java.io.FileOutputStream(localFile).use { outStream ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() // If local save fails, just print error and continue
                    }

                    val baos = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

                    withContext(Dispatchers.Main) {
                        // 1. Give the data to Firebase (it will sync in the background)
                        dbRef.child("profileImage").setValue(base64Image)

                        // 2. Immediately show the Toast and go back!
                        Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }

                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        e.printStackTrace()
                        // This will now show you the exact error message on your screen
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        saveButton.isEnabled = true
                    }
                }
            }
        } else {
            Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }
}