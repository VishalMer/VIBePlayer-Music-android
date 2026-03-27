package com.vishal.vibeplayer.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.manager.PlayerManager
import java.util.concurrent.TimeUnit
import com.vishal.vibeplayer.utils.OnSwipeTouchListener
import androidx.core.graphics.toColorInt
import java.util.Locale
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vishal.vibeplayer.adapter.SongAdapter
import android.app.AlertDialog
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.vishal.vibeplayer.database.AppDatabase
import com.vishal.vibeplayer.database.PlaylistSongEntity
import com.vishal.vibeplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import android.view.WindowManager

class PlayerFragment : Fragment() {

    private lateinit var runnable: Runnable
    private var handler = Handler(Looper.getMainLooper())

    private lateinit var imgPlayPause: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView

    private lateinit var txtPlayerTitle: TextView
    private lateinit var txtPlayerArtist: TextView
    private lateinit var imgPlayerArt: ImageView
    private lateinit var btnFavorite: View
    private lateinit var imgFavoriteIcon: ImageView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView

    // Moved these here so they can be accessed in onViewCreated
    private lateinit var btnPlayPause: View
    private lateinit var btnDownPlayer: View
    private lateinit var btnNext: ImageView
    private lateinit var btnPrevious: ImageView
    private lateinit var btnQueue: View
    private lateinit var btnAddToPlaylist: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player, container, false)

        // 1. Find all views (No click listeners here!)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        imgPlayPause = view.findViewById(R.id.imgPlayPause)
        btnDownPlayer = view.findViewById(R.id.btnDownPlayer)
        btnNext = view.findViewById(R.id.btnNext)
        btnPrevious = view.findViewById(R.id.btnPrev)
        btnQueue = view.findViewById(R.id.btnQueue)
        btnAddToPlaylist = view.findViewById(R.id.btnAddToPlaylist)

        btnFavorite = view.findViewById(R.id.btnFavorite)
        imgFavoriteIcon = view.findViewById(R.id.imgFavoriteIcon)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)

        seekBar = view.findViewById(R.id.seekBarPlayer)
        txtCurrentTime = view.findViewById(R.id.txtCurrentTime)
        txtTotalTime = view.findViewById(R.id.txtTotalTime)

        txtPlayerTitle = view.findViewById(R.id.txtPlayerTitle)
        txtPlayerArtist = view.findViewById(R.id.txtPlayerArtist)
        imgPlayerArt = view.findViewById(R.id.imgPlayerArt)

        txtPlayerTitle.isSelected = true

        return view
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.let { window ->
            // THE NUCLEAR FLAG: This forces the screen to ignore ALL safe areas and insets.
            // It will forcefully stretch your dynamic background to the absolute top of the phone.
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }

    override fun onPause() {
        super.onPause()
        activity?.window?.let { window ->
            // Remove the flag when you hit the back button so your Library and Home screens stay normal!
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Setup all click listeners safely!
        btnNext.setOnClickListener { PlayerManager.playNext(requireContext()) }
        btnPrevious.setOnClickListener { PlayerManager.playPrevious(requireContext()) }

        btnShuffle.setOnClickListener {
            PlayerManager.toggleShuffle()
            updateShuffleRepeatUI()
        }

        btnRepeat.setOnClickListener {
            PlayerManager.toggleRepeat()
            updateShuffleRepeatUI()
        }

        btnFavorite.setOnClickListener {
            PlayerManager.toggleFavorite(requireContext())
            updateFavoriteUI()
        }

        // --- THE FIX IS HERE ---
        btnAddToPlaylist.setOnClickListener {
            PlayerManager.currentSong?.let { currentSong ->
                showSelectPlaylistDialog(currentSong)
            } ?: Toast.makeText(requireContext(), "No song is playing!", Toast.LENGTH_SHORT).show()
        }

        btnPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying) PlayerManager.pause(requireContext())
            else PlayerManager.play(requireContext())
        }

        btnDownPlayer.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        btnQueue.setOnClickListener {
            showQueueBottomSheet()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    PlayerManager.mediaPlayer?.seekTo(progress)
                    txtCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        imgPlayerArt.setOnTouchListener(object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeLeft() {
                imgPlayerArt.animate().translationX(-1000f).alpha(0f).setDuration(200).withEndAction {
                    PlayerManager.playNext(requireContext())
                    imgPlayerArt.translationX = 1000f
                    imgPlayerArt.animate().translationX(0f).alpha(1f).setDuration(200).start()
                }.start()
            }

            override fun onSwipeRight() {
                imgPlayerArt.animate().translationX(1000f).alpha(0f).setDuration(200).withEndAction {
                    PlayerManager.playPrevious(requireContext())
                    imgPlayerArt.translationX = -1000f
                    imgPlayerArt.animate().translationX(0f).alpha(1f).setDuration(200).start()
                }.start()
            }
        })

        // Initial setup
        updateUI(view)

        PlayerManager.onPlayerStateChanged = {
            activity?.runOnUiThread {
                updateUI(view)
            }
        }
    }

    private fun updateUI(view: View) {
        PlayerManager.currentSong?.let { song ->
            txtPlayerTitle.text = song.title
            txtPlayerArtist.text = song.artist

            if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .asBitmap()
                    .load(song.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            if (isAdded) {
                                imgPlayerArt.setImageBitmap(resource)
                                updateDynamicBackground(view, resource)
                            }
                        }
                        override fun onLoadCleared(placeholder: Drawable?) {
                            imgPlayerArt.setImageDrawable(placeholder)
                        }
                    })
            } else {
                if (song.art != null) {
                    imgPlayerArt.setImageBitmap(song.art)
                    updateDynamicBackground(view, song.art)
                } else {
                    imgPlayerArt.setImageResource(R.drawable.bg_default_cover)
                    updateDynamicBackground(view, null)
                }
            }
        }

        updateShuffleRepeatUI()
        updateFavoriteUI()

        if (PlayerManager.isPlaying) {
            imgPlayPause.setImageResource(R.drawable.ic_pause)
            startSeekBarLoop()
        } else {
            imgPlayPause.setImageResource(R.drawable.ic_play)
            if (::runnable.isInitialized) handler.removeCallbacks(runnable)
        }

        PlayerManager.mediaPlayer?.let { player ->
            seekBar.max = player.duration
            txtTotalTime.text = formatTime(player.duration)
        }
    }

    private fun updateShuffleRepeatUI() {
        if (PlayerManager.isShuffleEnabled) {
            btnShuffle.setImageResource(R.drawable.ic_shuffle_on)
            btnShuffle.clearColorFilter()
        } else {
            btnShuffle.setImageResource(R.drawable.ic_shuffle)
            btnShuffle.setColorFilter(Color.WHITE)
        }

        if (PlayerManager.isRepeatEnabled) {
            btnRepeat.setImageResource(R.drawable.ic_repeat_on)
            btnRepeat.clearColorFilter()
        } else {
            btnRepeat.setImageResource(R.drawable.ic_repeat)
            btnRepeat.setColorFilter(Color.WHITE)
        }
    }

    private fun updateFavoriteUI() {
        val isFav = PlayerManager.favoriteSongs.contains(PlayerManager.currentSong?.path)
        if (isFav) {
            imgFavoriteIcon.setImageResource(R.drawable.ic_heart_fill)
        } else {
            imgFavoriteIcon.setImageResource(R.drawable.ic_heart)
        }
    }

    // ... (Keep the rest of your methods like showSelectPlaylistDialog, formatTime, updateDynamicBackground, etc. exactly the same) ...

    private fun showSelectPlaylistDialog(song: Song) {
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val playlists = db.playlistDao().getAllPlaylists()

            withContext(Dispatchers.Main) {
                if (playlists.isEmpty()) {
                    Toast.makeText(requireContext(), "No custom playlists found. Create one first!", Toast.LENGTH_LONG).show()
                    return@withContext
                }

                val playlistNames = playlists.map { it.name }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Add to...")
                    .setItems(playlistNames) { _, which ->
                        val selectedPlaylist = playlists[which]

                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

                            val existingSongs = db.playlistDao().getSongsInPlaylist(selectedPlaylist.id)
                            val isAlreadyAdded = existingSongs.any { it.songPath == song.path }

                            if (isAlreadyAdded) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Already in ${selectedPlaylist.name}!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val newEntry = PlaylistSongEntity(
                                    playlistId = selectedPlaylist.id,
                                    songPath = song.path ?: "",
                                    isOnline = song.isOnline
                                )
                                db.playlistDao().insertSongIntoPlaylist(newEntry)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Added to ${selectedPlaylist.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun startSeekBarLoop() {
        if (::runnable.isInitialized) handler.removeCallbacks(runnable)
        runnable = Runnable {
            PlayerManager.mediaPlayer?.let { player ->
                if (PlayerManager.isPlaying) {
                    seekBar.progress = player.currentPosition
                    txtCurrentTime.text = formatTime(player.currentPosition)
                    handler.postDelayed(runnable, 1000)
                }
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(ms: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun updateDynamicBackground(view: View, bitmap: Bitmap?) {
        val rootLayout = view.findViewById<View>(R.id.playerRootLayout)
        val defaultDarkColor = "#121212".toColorInt()

        if (bitmap == null) {
            rootLayout.setBackgroundColor(defaultDarkColor)
            return
        }

        Palette.from(bitmap).generate { palette ->
            val extractedColor = palette?.darkVibrantSwatch?.rgb
                ?: palette?.dominantSwatch?.rgb
                ?: defaultDarkColor

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(extractedColor, defaultDarkColor)
            )
            gradientDrawable.cornerRadius = 0f

            rootLayout.background = gradientDrawable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        PlayerManager.onPlayerStateChanged = null
    }

    private fun showQueueBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_queue, null)
        bottomSheetDialog.setContentView(view)

        val rvQueue = view.findViewById<RecyclerView>(R.id.rvQueue)
        rvQueue.layoutManager = LinearLayoutManager(requireContext())

        var fullList = PlayerManager.currentPlaylist.toMutableList()
        var currentIndex = PlayerManager.currentIndex

        if (fullList.isEmpty() || currentIndex < 0) {
            fullList = PlayerManager.allSongs.toMutableList()
            currentIndex = fullList.indexOfFirst { it.path == PlayerManager.currentSong?.path }
            if (currentIndex == -1) currentIndex = 0

            PlayerManager.currentPlaylist = fullList
            PlayerManager.currentIndex = currentIndex
            PlayerManager.originalPlaylist = fullList.toList()
        }

        if (fullList.isEmpty()) return

        val displayQueue = fullList.subList(currentIndex, fullList.size).toMutableList()

        val queueAdapter = SongAdapter(
            songs = displayQueue,
            onSongClicked = { clickedSong ->
                val clickedDisplayIndex = displayQueue.indexOf(clickedSong)
                val trueGlobalIndex = PlayerManager.currentIndex + clickedDisplayIndex

                PlayerManager.startPlaying(requireContext(), PlayerManager.currentPlaylist, trueGlobalIndex)
                bottomSheetDialog.dismiss()
            },
            onMoreOptionsClicked = { }
        )
        rvQueue.adapter = queueAdapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                queueAdapter.moveSong(fromPosition, toPosition)

                val movedSong = displayQueue.removeAt(fromPosition)
                displayQueue.add(toPosition, movedSong)

                val globalFrom = PlayerManager.currentIndex + fromPosition
                val globalTo = PlayerManager.currentIndex + toPosition

                val mutableGlobalList = PlayerManager.currentPlaylist.toMutableList()
                val globalMoved = mutableGlobalList.removeAt(globalFrom)
                mutableGlobalList.add(globalTo, globalMoved)

                PlayerManager.currentPlaylist = mutableGlobalList

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvQueue)
        bottomSheetDialog.show()
    }
}