package com.aurix.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private data class AppTile(
        val name: String,
        val packageName: String,
        val logo: Int,
        val banner: Int,
        val accent: Int
    )

    private val apps = listOf(
        AppTile("NETFLIX", "com.netflix.ninja", R.drawable.netflix_logo, R.drawable.netflix_banner, Color.rgb(229, 9, 20)),
        AppTile("YouTube", "com.google.android.youtube.tv", R.drawable.youtube_logo, R.drawable.youtube_banner, Color.WHITE),
        AppTile("TV", "com.android.tv", R.drawable.tv_logo, R.drawable.tv_banner, Color.WHITE)
    )

    private lateinit var root: FrameLayout
    private lateinit var title: TextView
    private lateinit var banner: ImageView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private val bubbles = mutableListOf<ImageView>()
    private var selected = 0

    private val clockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildHome()
    }

    override fun onResume() {
        super.onResume()
        if (::clock.isInitialized) refreshClock()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { changeSelection(-1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { changeSelection(1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { launchSelected(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun buildHome() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
        }

        val brand = TextView(this).apply {
            text = "A.U.R.I.X."
            setTextColor(Color.WHITE)
            textSize = 13f
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isFocusable = true
            setOnClickListener { openSettings() }
            setOnFocusChangeListener { v, focused -> v.alpha = if (focused) 1f else 0.78f }
            contentDescription = "A.U.R.I.X. Settings"
        }
        val brandParams = FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP or Gravity.START)
        brandParams.setMargins(dp(24), dp(8), 0, 0)
        root.addView(brand, brandParams)

        val timeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        clock = text("", 12, Color.WHITE, true)
        date = text("", 9, Color.WHITE, false)
        timeBox.addView(clock)
        timeBox.addView(date)
        val timeParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        timeParams.setMargins(0, dp(8), dp(24), 0)
        root.addView(timeBox, timeParams)

        banner = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected app banner"
        }
        val bannerParams = FrameLayout.LayoutParams(dp(360), dp(170), Gravity.CENTER)
        bannerParams.bottomMargin = dp(115)
        root.addView(banner, bannerParams)

        title = text("", 27, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val titleParams = FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER)
        titleParams.topMargin = dp(115)
        root.addView(title, titleParams)

        apps.forEachIndexed { index, app ->
            val bubble = ImageView(this).apply {
                setImageResource(app.logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(13), dp(13), dp(13), dp(13))
                background = glassBubble()
                elevation = dp(5).toFloat()
                contentDescription = app.name
                isFocusable = false
                setOnClickListener { selected = index; updateSelector(true); launchSelected() }
            }
            val params = FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            params.bottomMargin = dp(8)
            root.addView(bubble, params)
            bubbles.add(bubble)
        }

        setContentView(root)
        refreshClock()
        updateSelector(false)
        root.requestFocus()
    }

    private fun changeSelection(direction: Int) {
        selected = (selected + direction + apps.size) % apps.size
        updateSelector(true)
    }

    private fun updateSelector(animate: Boolean) {
        val app = apps[selected]
        title.text = app.name
        title.setTextColor(app.accent)
        banner.setImageResource(app.banner)

        bubbles.forEachIndexed { index, bubble ->
            val relative = (index - selected + apps.size) % apps.size
            val x = when (relative) { 1 -> -dp(64).toFloat(); 2 -> dp(64).toFloat(); else -> 0f }
            val y = if (relative == 0) -dp(28).toFloat() else 0f
            val scale = if (relative == 0) 1.12f else 0.88f
            val alpha = if (relative == 0) 1f else 0.72f
            if (animate) {
                bubble.animate().translationX(x).translationY(y).scaleX(scale).scaleY(scale).alpha(alpha)
                    .setDuration(210).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            } else {
                bubble.translationX = x
                bubble.translationY = y
                bubble.scaleX = scale
                bubble.scaleY = scale
                bubble.alpha = alpha
            }
        }
    }

    private fun launchSelected() {
        val app = apps[selected]
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "${app.name} is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        try { startActivity(intent) }
        catch (_: Exception) { Toast.makeText(this, "${app.name} could not be opened.", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings() {
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        catch (_: Exception) {
            try { startActivity(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings")) }
            catch (_: Exception) { Toast.makeText(this, "TV Settings could not be opened.", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun refreshClock() {
        clock.text = clockFormat.format(Date())
        date.text = dateFormat.format(Date())
    }

    private fun glassBubble() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(66, 66, 66), Color.rgb(24, 24, 24), Color.rgb(8, 8, 8))
    ).apply { shape = GradientDrawable.OVAL; setStroke(dp(1), Color.rgb(75, 75, 75)) }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}