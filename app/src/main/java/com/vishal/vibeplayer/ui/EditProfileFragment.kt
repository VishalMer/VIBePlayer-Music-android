package com.vishal.vibeplayer.ui

import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.github.dhaval2404.imagepicker.ImagePicker
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
    private var isRemovingImage = false
    private lateinit var imgEditAvatar: ImageView

    private lateinit var etEditName: EditText
    private lateinit var etEditUsername: EditText
    private lateinit var etEditBio: EditText

    private val profileImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data!!

            selectedImageUri = uri
            isRemovingImage = false

            val localFile = File(requireContext().filesDir, "vibe_profile.jpg")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }

            Glide.with(this)
                .load(localFile)
                .centerCrop()
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imgEditAvatar)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgEditAvatar = view.findViewById(R.id.imgAvatarInner)
        etEditName = view.findViewById(R.id.etEditName)
        etEditUsername = view.findViewById(R.id.etEditUsername)
        etEditBio = view.findViewById(R.id.etEditBio)
        val btnSave = view.findViewById<View>(R.id.btnSaveProfile)

        val currentUser = FirebaseAuth.getInstance().currentUser
        val btnEditBack = view.findViewById<View>(R.id.btnEditBack)
        btnEditBack.setOnClickListener {
            findNavController().popBackStack()
        }

        val prefs = requireContext().getSharedPreferences("VibeProfilePrefs", Context.MODE_PRIVATE)
        val localName = prefs.getString("name", currentUser?.displayName ?: "")
        val localUsername = prefs.getString("username", "")
        val localBio = prefs.getString("bio", "")

        etEditName.setText(localName)
        if (!localUsername.isNullOrEmpty()) etEditUsername.setText(localUsername)
        if (!localBio.isNullOrEmpty()) etEditBio.setText(localBio)

        if (currentUser != null) {
            val dbRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            dbRef.get().addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val existingUsername = snapshot.child("username").getValue(String::class.java)
                val existingBio = snapshot.child("bio").getValue(String::class.java)

                if (localUsername.isNullOrEmpty() && !existingUsername.isNullOrEmpty()) etEditUsername.setText(existingUsername)
                if (localBio.isNullOrEmpty() && !existingBio.isNullOrEmpty()) etEditBio.setText(existingBio)
            }
        }

        val localFile = File(requireContext().filesDir, "vibe_profile.jpg")

        if (localFile.exists()) {
            Glide.with(this)
                .load(localFile)
                .centerCrop()
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.default_pp)
                .error(R.drawable.default_pp)
                .signature(ObjectKey(localFile.lastModified()))
                .into(imgEditAvatar)
        } else if (currentUser != null) {
            Glide.with(this)
                .load(R.drawable.default_pp)
                .centerCrop()
                .circleCrop()
                .into(imgEditAvatar)

            val dbRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            dbRef.child("profileImage").get().addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val base64Image = snapshot.getValue(String::class.java)
                if (base64Image != null) {
                    try {
                        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        Glide.with(this@EditProfileFragment)
                            .load(bitmap)
                            .centerCrop()
                            .circleCrop()
                            .into(imgEditAvatar)

                        java.io.FileOutputStream(localFile).use { outStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        } else {
            Glide.with(this)
                .load(R.drawable.default_pp)
                .centerCrop()
                .circleCrop()
                .into(imgEditAvatar)
        }

        imgEditAvatar.setOnClickListener { showImageOptionsDialog() }

        btnSave.setOnClickListener {
            val newName = etEditName.text.toString().trim()
            val newUsername = etEditUsername.text.toString().trim()
            val newBio = etEditBio.text.toString().trim()

            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSave.isEnabled = false
            saveProfileData(newName, newUsername, newBio, btnSave)
        }
    }

    private fun showImageOptionsDialog() {
        val options = arrayOf("Choose from Gallery", "Remove Photo")
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        ImagePicker.with(this)
                            .galleryOnly()
                            .cropSquare()
                            .compress(512)
                            .maxResultSize(500, 500)
                            .createIntent { intent: android.content.Intent ->
                                profileImageLauncher.launch(intent)
                            }
                    }
                    1 -> removeProfilePicture()
                }
            }
            .show()
    }

    private fun removeProfilePicture() {
        selectedImageUri = null
        isRemovingImage = true
        val localFile = File(requireContext().filesDir, "vibe_profile.jpg")
        if (localFile.exists()) localFile.delete()

        Glide.with(this)
            .load(R.drawable.default_pp)
            .centerCrop()
            .circleCrop()
            .into(imgEditAvatar)
    }

    private fun saveProfileData(newName: String, newUsername: String, newBio: String, saveButton: View) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val dbRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid)

        val prefs = requireContext().getSharedPreferences("VibeProfilePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("name", newName)
            .putString("username", newUsername)
            .putString("bio", newBio)
            .apply()

        val profileData = mapOf(
            "name" to newName,
            "username" to newUsername,
            "bio" to newBio
        )
        dbRef.updateChildren(profileData)

        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
        user.updateProfile(profileUpdates)

        if (isRemovingImage) {
            val localFile = File(requireContext().filesDir, "vibe_profile.jpg")
            if (localFile.exists()) localFile.delete()
            dbRef.child("profileImage").removeValue()

            Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
        else if (selectedImageUri != null) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(selectedImageUri!!)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    if (originalBitmap == null) throw Exception("Could not read image file.")

                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 256, 256, true)
                    try {
                        val localFile = File(requireContext().filesDir, "vibe_profile.jpg")
                        java.io.FileOutputStream(localFile).use { outStream ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    val baos = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

                    withContext(Dispatchers.Main) {
                        dbRef.child("profileImage").setValue(base64Image)
                        Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }

                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
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