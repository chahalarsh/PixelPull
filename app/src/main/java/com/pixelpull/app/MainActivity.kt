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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: TextInputEditText
    private lateinit var selectLocationButton: MaterialButton
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
        selectLocationButton = findViewById(R.id.selectLocationButton)
        selectTimeButton = findViewById(R.id.selectTimeButton)
        updateNowButton = findViewById(R.id.updateNowButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        // Load saved URL
        val savedUrl = PreferencesManager.getWallpaperUrl(this)
        urlInput.setText(savedUrl)

        // Load saved location
        updateLocationDisplay()

        // Load saved schedule
        updateScheduleDisplay()

        // Set up button listeners
        selectLocationButton.setOnClickListener {
            showLocationPicker()
        }
        
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
                // Get saved location
                val location = PreferencesManager.getWallpaperLocation(this)
                // Update wallpaper
                updateWallpaperNow(url, location)
            }
        }

        // Request exact alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkExactAlarmPermission()
        }
        
        // Create notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun showTimePicker() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_time_selector, null)
        bottomSheetDialog.setContentView(view)
        
        // Get current time or default
        val hour = PreferencesManager.getScheduleHour(this)
        val minute = PreferencesManager.getScheduleMinute(this)
        
        val currentHour = if (hour >= 0) hour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentMinute = if (minute >= 0) minute else 0
        
        // Setup NumberPickers
        val hourPicker = view.findViewById<NumberPicker>(R.id.hourPicker)
        val minutePicker = view.findViewById<NumberPicker>(R.id.minutePicker)
        val ampmPicker = view.findViewById<NumberPicker>(R.id.ampmPicker)
        
        // Hour picker (1-12)
        hourPicker?.apply {
            minValue = 1
            maxValue = 12
            value = if (currentHour == 0) 12 else if (currentHour > 12) currentHour - 12 else currentHour
            wrapSelectorWheel = true
            setOnValueChangedListener { _, _, _ ->
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        
        // Minute picker (0-59)
        minutePicker?.apply {
            minValue = 0
            maxValue = 59
            value = currentMinute
            wrapSelectorWheel = true
            setFormatter { String.format("%02d", it) }
            setOnValueChangedListener { _, _, _ ->
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        
        // AM/PM picker
        ampmPicker?.apply {
            minValue = 0
            maxValue = 1
            displayedValues = arrayOf("AM", "PM")
            value = if (currentHour >= 12) 1 else 0
            wrapSelectorWheel = false
            setOnValueChangedListener { _, _, _ ->
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        
        // Set Time button - Save the selected time
        view.findViewById<MaterialButton>(R.id.setTimeButton)?.setOnClickListener {
            val selectedHour12 = hourPicker?.value ?: 12
            val isAM = ampmPicker?.value == 0
            val selectedMinute = minutePicker?.value ?: 0
            
            // Convert to 24-hour format
            val selectedHour24 = when {
                selectedHour12 == 12 && isAM -> 0  // 12 AM = 0
                selectedHour12 == 12 && !isAM -> 12  // 12 PM = 12
                !isAM -> selectedHour12 + 12  // PM hours (1-11)
                else -> selectedHour12  // AM hours (1-11)
            }
            
            // Save URL first
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                PreferencesManager.saveWallpaperUrl(this, url)
            }
            
            // Save schedule
            PreferencesManager.saveScheduleTime(this, selectedHour24, selectedMinute)
            
            // Schedule alarm
            AlarmScheduler.scheduleWallpaperUpdate(this, selectedHour24, selectedMinute)
            
            // Update display
            updateScheduleDisplay()
            
            // Show confirmation
            val timeString = formatTime(selectedHour24, selectedMinute)
            Toast.makeText(this, getString(R.string.schedule_saved, timeString), Toast.LENGTH_LONG).show()
            
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.show()
    }
    


    private fun showLocationPicker() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_location_selector, null)
        bottomSheetDialog.setContentView(view)
        
        // Get current selection
        val currentLocation = PreferencesManager.getWallpaperLocation(this)
        
        // Find views
        val optionHome = view.findViewById<MaterialCardView>(R.id.optionHome)
        val optionLock = view.findViewById<MaterialCardView>(R.id.optionLock)
        val optionBoth = view.findViewById<MaterialCardView>(R.id.optionBoth)
        
        val iconHomeSelected = view.findViewById<ImageView>(R.id.iconHomeSelected)
        val iconLockSelected = view.findViewById<ImageView>(R.id.iconLockSelected)
        val iconBothSelected = view.findViewById<ImageView>(R.id.iconBothSelected)
        
        // Update UI based on current selection
        fun updateSelection(location: PreferencesManager.WallpaperLocation) {
            iconHomeSelected.visibility = if (location == PreferencesManager.WallpaperLocation.HOME) View.VISIBLE else View.GONE
            iconLockSelected.visibility = if (location == PreferencesManager.WallpaperLocation.LOCK) View.VISIBLE else View.GONE
            iconBothSelected.visibility = if (location == PreferencesManager.WallpaperLocation.BOTH) View.VISIBLE else View.GONE
            
            // Update card stroke colors
            optionHome.strokeColor = if (location == PreferencesManager.WallpaperLocation.HOME) 
                android.graphics.Color.parseColor("#FFFFFF") else android.graphics.Color.parseColor("#3A3A3A")
            optionLock.strokeColor = if (location == PreferencesManager.WallpaperLocation.LOCK) 
                android.graphics.Color.parseColor("#FFFFFF") else android.graphics.Color.parseColor("#3A3A3A")
            optionBoth.strokeColor = if (location == PreferencesManager.WallpaperLocation.BOTH) 
                android.graphics.Color.parseColor("#FFFFFF") else android.graphics.Color.parseColor("#3A3A3A")
        }
        
        // Show current selection
        updateSelection(currentLocation)
        
        // Set click listeners
        optionHome.setOnClickListener {
            PreferencesManager.saveWallpaperLocation(this, PreferencesManager.WallpaperLocation.HOME)
            updateSelection(PreferencesManager.WallpaperLocation.HOME)
            updateLocationDisplay()
            bottomSheetDialog.dismiss()
        }
        
        optionLock.setOnClickListener {
            PreferencesManager.saveWallpaperLocation(this, PreferencesManager.WallpaperLocation.LOCK)
            updateSelection(PreferencesManager.WallpaperLocation.LOCK)
            updateLocationDisplay()
            bottomSheetDialog.dismiss()
        }
        
        optionBoth.setOnClickListener {
            PreferencesManager.saveWallpaperLocation(this, PreferencesManager.WallpaperLocation.BOTH)
            updateSelection(PreferencesManager.WallpaperLocation.BOTH)
            updateLocationDisplay()
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.show()
    }

    private fun updateLocationDisplay() {
        val location = PreferencesManager.getWallpaperLocation(this)
        selectLocationButton.text = when (location) {
            PreferencesManager.WallpaperLocation.HOME -> getString(R.string.location_home)
            PreferencesManager.WallpaperLocation.LOCK -> getString(R.string.location_lock)
            PreferencesManager.WallpaperLocation.BOTH -> getString(R.string.location_both)
        }
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

    private fun updateWallpaperNow(url: String, location: PreferencesManager.WallpaperLocation) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Show progress IN button - hide button text and icon
                updateNowButton.text = ""
                updateNowButton.icon = null
                progressBar.visibility = View.VISIBLE
                statusText.visibility = View.GONE
                updateNowButton.isEnabled = false

                // Download and set wallpaper
                val result = WallpaperService.downloadAndSetWallpaper(this@MainActivity, url, location)

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
