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

    private lateinit var btnFavorite: ImageView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player, container, false)

        val btnPlayPause = view.findViewById<View>(R.id.btnPlayPause)
        imgPlayPause = view.findViewById(R.id.imgPlayPause)
        val btnDownPlayer = view.findViewById<View>(R.id.btnDownPlayer)
        val btnNext = view.findViewById<ImageView>(R.id.btnNext)
        val btnPrevious = view.findViewById<ImageView>(R.id.btnPrev)
        val btnQueue = view.findViewById<View>(R.id.btnQueue)

        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)

        seekBar = view.findViewById(R.id.seekBarPlayer)
        txtCurrentTime = view.findViewById(R.id.txtCurrentTime)
        txtTotalTime = view.findViewById(R.id.txtTotalTime)

        txtPlayerTitle = view.findViewById(R.id.txtPlayerTitle)
        txtPlayerArtist = view.findViewById(R.id.txtPlayerArtist)
        imgPlayerArt = view.findViewById(R.id.imgPlayerArt)

        txtPlayerTitle.isSelected = true

        btnNext?.setOnClickListener { PlayerManager.playNext(requireContext()) }
        btnPrevious?.setOnClickListener { PlayerManager.playPrevious(requireContext()) }
        btnShuffle.setOnClickListener { PlayerManager.toggleShuffle() }
        btnRepeat.setOnClickListener { PlayerManager.toggleRepeat() }
        btnFavorite.setOnClickListener { PlayerManager.toggleFavorite(requireContext()) }

        btnPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying) PlayerManager.pause(requireContext())
            else PlayerManager.play(requireContext())
        }

        btnDownPlayer.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Only set the click listener IF the button was successfully found on the screen!
        if (btnQueue != null) {
            btnQueue.setOnClickListener {
                showQueueBottomSheet()
            }
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
                // Animate image sliding off to the left
                imgPlayerArt.animate().translationX(-1000f).alpha(0f).setDuration(200).withEndAction {
                    PlayerManager.playNext(requireContext())

                    // Instantly teleport the invisible image to the right side, then slide it in!
                    imgPlayerArt.translationX = 1000f
                    imgPlayerArt.animate().translationX(0f).alpha(1f).setDuration(200).start()
                }.start()
            }

            override fun onSwipeRight() {
                // Animate image sliding off to the right
                imgPlayerArt.animate().translationX(1000f).alpha(0f).setDuration(200).withEndAction {
                    PlayerManager.playPrevious(requireContext())

                    // Instantly teleport the invisible image to the left side, then slide it in!
                    imgPlayerArt.translationX = -1000f
                    imgPlayerArt.animate().translationX(0f).alpha(1f).setDuration(200).start()
                }.start()
            }
        })

        updateUI(view)

        PlayerManager.onPlayerStateChanged = {
            activity?.runOnUiThread {
                updateUI(view)
            }
        }

        return view
    }

    private fun updateUI(view: View) {
        PlayerManager.currentSong?.let { song ->
            txtPlayerTitle.text = song.title
            txtPlayerArtist.text = song.artist

            // --- SMART IMAGE & BACKGROUND LOADER ---
            if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {
                // ONLINE MODE: Download with Glide, set image, THEN extract Palette colors
                Glide.with(this)
                    .asBitmap()
                    .load(song.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            if (isAdded) { // Safety check to ensure fragment is still open
                                imgPlayerArt.setImageBitmap(resource)
                                updateDynamicBackground(view, resource)
                            }
                        }
                        override fun onLoadCleared(placeholder: Drawable?) {
                            imgPlayerArt.setImageDrawable(placeholder)
                        }
                    })
            } else {
                // OFFLINE MODE: Use local bitmap directly
                if (song.art != null) {
                    imgPlayerArt.setImageBitmap(song.art)
                    updateDynamicBackground(view, song.art)
                } else {
                    // --- UPDATE THIS LINE ---
                    imgPlayerArt.setImageResource(R.drawable.bg_default_cover)
                    updateDynamicBackground(view, null)
                }
            }
        }

        if (PlayerManager.isShuffleEnabled) {
            btnShuffle.setColorFilter("#1DB954".toColorInt())
        } else {
            btnShuffle.setColorFilter(Color.WHITE)
        }

        if (PlayerManager.isRepeatEnabled) {
            btnRepeat.setColorFilter("#1DB954".toColorInt())
        } else {
            btnRepeat.setColorFilter(Color.WHITE)
        }

        val isFav = PlayerManager.favoriteSongs.contains(PlayerManager.currentSong?.path)
        if (isFav) {
            btnFavorite.setImageResource(android.R.drawable.star_on)
            btnFavorite.setColorFilter("#1DB954".toColorInt())
        } else {
            btnFavorite.setImageResource(android.R.drawable.star_off)
            btnFavorite.setColorFilter(Color.WHITE)
        }

        if (PlayerManager.isPlaying) {
            imgPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            startSeekBarLoop()
        } else {
            imgPlayPause.setImageResource(android.R.drawable.ic_media_play)
            if (::runnable.isInitialized) handler.removeCallbacks(runnable)
        }

        PlayerManager.mediaPlayer?.let { player ->
            seekBar.max = player.duration
            txtTotalTime.text = formatTime(player.duration)
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
        return String.format(Locale.US, "%02d:%02d", minutes, seconds) // Added Locale.US
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

        // 1. Get the full active playlist and where we currently are
        var fullList = PlayerManager.currentPlaylist.toMutableList()
        var currentIndex = PlayerManager.currentIndex

        // ==========================================
        // --- THE CRASH FIX: THE COLD BOOT REBUILD ---
        // ==========================================
        // If the app just opened and the queue is lost, rebuild it instantly!
        if (fullList.isEmpty() || currentIndex < 0) {
            fullList = PlayerManager.allSongs.toMutableList()
            currentIndex = fullList.indexOfFirst { it.path == PlayerManager.currentSong?.path }
            if (currentIndex == -1) currentIndex = 0 // Failsafe

            // Sync it back to the brain so it remembers!
            PlayerManager.currentPlaylist = fullList
            PlayerManager.currentIndex = currentIndex
            PlayerManager.originalPlaylist = fullList.toList()
        }

        // Final safety net just in case the phone has literally 0 songs
        if (fullList.isEmpty()) return

        // 2. SLICE THE LIST: We only want the UI to show the current song and what's next!
        val displayQueue = fullList.subList(currentIndex, fullList.size).toMutableList()

        // 3. Setup the Adapter
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

        // 4. Setup Drag to Reorder
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // Animate visual swap
                queueAdapter.moveSong(fromPosition, toPosition)

                // Update our UI list
                val movedSong = displayQueue.removeAt(fromPosition)
                displayQueue.add(toPosition, movedSong)

                // SYNC THE BRAIN: Update the global playlist
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