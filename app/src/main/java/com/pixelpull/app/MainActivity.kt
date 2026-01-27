package com.pixelpull.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: TextInputEditText
    private lateinit var selectTimeButton: MaterialButton
    private lateinit var updateNowButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable camera cutout usage in landscape
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // Enable edge-to-edge for portrait mode (draw behind status bar)
        enableEdgeToEdge()
        
        setContentView(R.layout.activity_main)

        // Set status bar color and fullscreen mode based on orientation
        updateStatusBarColor()
        enableFullscreenInLandscape()

        // Initialize views
        urlInput = findViewById(R.id.urlInput)
        selectTimeButton = findViewById(R.id.selectTimeButton)
        updateNowButton = findViewById(R.id.updateNowButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        // Load saved URL
        val savedUrl = PreferencesManager.getWallpaperUrl(this)
        urlInput.setText(savedUrl)

        // Load saved schedule
        updateScheduleDisplay()

        // Set up button listeners
        selectTimeButton.setOnClickListener {
            showTimePicker()
        }

        updateNowButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.enter_url_first, Toast.LENGTH_SHORT).show()
            } else {
                // Save URL
                PreferencesManager.saveWallpaperUrl(this, url)
                // Update wallpaper
                updateWallpaperNow(url)
            }
        }

        // Request exact alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkExactAlarmPermission()
        }
    }

    private fun showTimePicker() {
        val hour = PreferencesManager.getScheduleHour(this)
        val minute = PreferencesManager.getScheduleMinute(this)

        val currentHour = if (hour >= 0) hour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentMinute = if (minute >= 0) minute else Calendar.getInstance().get(Calendar.MINUTE)

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText("Select update time")
            .build()

        picker.addOnPositiveButtonClickListener {
            val selectedHour = picker.hour
            val selectedMinute = picker.minute

            // Save URL first
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                PreferencesManager.saveWallpaperUrl(this, url)
            }

            // Save schedule
            PreferencesManager.saveScheduleTime(this, selectedHour, selectedMinute)

            // Schedule alarm
            AlarmScheduler.scheduleWallpaperUpdate(this, selectedHour, selectedMinute)

            // Update display
            updateScheduleDisplay()

            // Show confirmation
            val timeString = formatTime(selectedHour, selectedMinute)
            Toast.makeText(
                this,
                getString(R.string.schedule_saved, timeString),
                Toast.LENGTH_LONG
            ).show()
        }

        picker.show(supportFragmentManager, "TIME_PICKER")
    }

    private fun updateScheduleDisplay() {
        val hour = PreferencesManager.getScheduleHour(this)
        val minute = PreferencesManager.getScheduleMinute(this)

        if (hour >= 0 && minute >= 0) {
            val timeString = formatTime(hour, minute)
            selectTimeButton.text = timeString
        } else {
            selectTimeButton.text = getString(R.string.select_time)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        return format.format(calendar.time)
    }

    private fun updateWallpaperNow(url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Show progress IN button - hide button text and icon
                updateNowButton.text = ""
                updateNowButton.icon = null
                progressBar.visibility = View.VISIBLE
                statusText.visibility = View.GONE
                updateNowButton.isEnabled = false

                // Download and set wallpaper
                val result = WallpaperService.downloadAndSetWallpaper(this@MainActivity, url)

                // Hide progress
                progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    // Show success message IN button
                    statusText.text = getString(R.string.wallpaper_updated)
                    statusText.visibility = View.VISIBLE
                    // Reset after 2 seconds
                    updateNowButton.postDelayed({
                        statusText.visibility = View.GONE
                        updateNowButton.text = getString(R.string.update_now)
                        updateNowButton.isEnabled = true
                    }, 2000)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    statusText.text = "Failed"
                    statusText.visibility = View.VISIBLE
                    updateNowButton.postDelayed({
                        statusText.visibility = View.GONE
                        updateNowButton.text = getString(R.string.update_now)
                        updateNowButton.isEnabled = true
                    }, 2000)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                statusText.text = "Error"
                statusText.visibility = View.VISIBLE
                updateNowButton.postDelayed({
                    statusText.visibility = View.GONE
                    updateNowButton.text = getString(R.string.update_now)
                    updateNowButton.isEnabled = true
                }, 2000)
                e.printStackTrace()
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Show explanation and request permission
                Toast.makeText(
                    this,
                    "Please grant exact alarm permission for scheduled wallpaper updates",
                    Toast.LENGTH_LONG
                ).show()

                // Open settings
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun updateStatusBarColor() {
        val orientation = resources.configuration.orientation
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape: Use app background color (independent of system settings)
            window.statusBarColor = android.graphics.Color.parseColor("#141414")
        } else {
            // Portrait: Transparent to show gradient
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    private fun enableEdgeToEdge() {
        val orientation = resources.configuration.orientation
        if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            // Portrait: Draw behind status bar with transparent status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
    }

    private fun enableFullscreenInLandscape() {
        val orientation = resources.configuration.orientation
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // Enable immersive fullscreen mode in landscape (like YouTube/games)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(android.view.WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = 
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
    }
}
