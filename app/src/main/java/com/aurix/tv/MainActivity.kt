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
    private lateinit var brand: TextView
    private lateinit var banner: ImageView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private val bubbles = mutableListOf<ImageView>()
    private var selected = 1
    private var settingsFocused = false

    private val clockFormat = SimpleDateFormat("h : mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd / MM / yyyy", Locale.getDefault())

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
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!settingsFocused) {
                    settingsFocused = true
                    brand.requestFocus()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (settingsFocused) {
                    settingsFocused = false
                    root.requestFocus()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!settingsFocused) changeSelection(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!settingsFocused) changeSelection(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (settingsFocused) openSettings() else launchSelected()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun buildHome() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
        }

        brand = TextView(this).apply {
            text = "A.U.R.I.X"
            setTextColor(Color.WHITE)
            textSize = 25f
            letterSpacing = 0.04f
            typeface = customFont()
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { openSettings() }
            setOnFocusChangeListener { v, focused -> v.alpha = if (focused) 1f else 0.82f }
            contentDescription = "A.U.R.I.X Settings"
        }
        val brandParams = FrameLayout.LayoutParams(-2, dp(54), Gravity.TOP or Gravity.START)
        brandParams.setMargins(dp(42), dp(24), 0, 0)
        root.addView(brand, brandParams)

        val timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clock = text("", 27, Color.WHITE, true)
        date = text("", 18, Color.WHITE, true)
        timeBox.addView(clock)
        timeBox.addView(date)
        val timeParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        timeParams.setMargins(0, dp(20), dp(42), 0)
        root.addView(timeBox, timeParams)

        banner = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected app"
        }
        val bannerParams = FrameLayout.LayoutParams(dp(610), dp(270), Gravity.CENTER)
        bannerParams.bottomMargin = dp(145)
        root.addView(banner, bannerParams)

        apps.forEachIndexed { index, app ->
            val bubble = ImageView(this).apply {
                setImageResource(app.logo)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(42), dp(42), dp(42), dp(42))
                background = glassBubble(false)
                elevation = dp(8).toFloat()
                contentDescription = app.name
                isFocusable = false
                setOnClickListener {
                    selected = index
                    updateSelector(true)
                    launchSelected()
                }
            }
            val params = FrameLayout.LayoutParams(dp(210), dp(210), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            params.bottomMargin = -dp(105)
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
        banner.setImageResource(app.banner)

        bubbles.forEachIndexed { index, bubble ->
            val relative = (index - selected + apps.size) % apps.size
            val isSelected = relative == 0
            val size = if (isSelected) dp(230) else dp(210)
            val params = bubble.layoutParams as FrameLayout.LayoutParams
            params.width = size
            params.height = size
            params.bottomMargin = if (isSelected) dp(18) else -dp(105)
            bubble.layoutParams = params

            val x = when (relative) {
                1 -> -dp(238).toFloat()
                2 -> dp(238).toFloat()
                else -> 0f
            }
            bubble.background = glassBubble(isSelected)
            bubble.setPadding(
                if (isSelected) dp(48) else dp(42),
                if (isSelected) dp(48) else dp(42),
                if (isSelected) dp(48) else dp(42),
                if (isSelected) dp(48) else dp(42)
            )
            bubble.translationZ = if (isSelected) dp(12).toFloat() else dp(5).toFloat()
            val scale = if (isSelected) 1f else 1f
            val alpha = if (isSelected) 1f else 0.82f

            if (animate) {
                bubble.animate().translationX(x).translationY(0f).scaleX(scale).scaleY(scale).alpha(alpha)
                    .setDuration(230)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                bubble.translationX = x
                bubble.translationY = 0f
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
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "${app.name} could not be opened.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"))
            } catch (_: Exception) {
                Toast.makeText(this, "TV Settings could not be opened.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshClock() {
        val now = Date()
        clock.text = clockFormat.format(now)
        date.text = dateFormat.format(now)
    }

    private fun glassBubble(selected: Boolean): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.argb(if (selected) 105 else 82, 170, 170, 170),
            Color.argb(if (selected) 78 else 58, 75, 75, 75),
            Color.argb(210, 12, 12, 12)
        )
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(if (selected) 2 else 1), Color.argb(if (selected) 190 else 115, 225, 225, 225))
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) customFont() else Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
        }

    private fun customFont(): Typeface = try {
        if (android.os.Build.VERSION.SDK_INT >= 26) resources.getFont(R.font.arial_black)
        else Typeface.create("sans-serif-black", Typeface.NORMAL)
    } catch (_: Exception) {
        Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
