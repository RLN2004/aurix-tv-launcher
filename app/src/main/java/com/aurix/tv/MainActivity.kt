package com.aurix.tv

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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
        val tagline: String,
        val packageName: String,
        val logo: Int?, // null -> built-in globe icon
        val accent: Int
    )

    private val apps = listOf(
        AppTile("YouTube", "Videos & music", "com.google.android.youtube.tv", R.drawable.youtube_logo, Color.rgb(255, 64, 64)),
        AppTile("TV", "Live TV & inputs", "com.android.tv", R.drawable.tv_logo, Color.rgb(77, 163, 255)),
        AppTile("Netflix", "Movies & shows", "com.netflix.ninja", R.drawable.netflix_logo, Color.rgb(229, 9, 20)),
        AppTile("Browser", "Rhenium", "com.nitesh.rhenium", null, Color.rgb(114, 214, 194))
    )

    private lateinit var backdrop: Backdrop
    private lateinit var brand: TextView
    private lateinit var settingsHint: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var hero: LinearLayout
    private lateinit var heroTag: TextView
    private lateinit var heroName: TextView
    private lateinit var heroStatus: TextView
    private val tiles = mutableListOf<FrameLayout>()

    private var selected = 0
    private var settingsFocused = false
    private var accentAnimator: ValueAnimator? = null

    private val clockFormat = SimpleDateFormat("hh : mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd / MM / yyyy", Locale.getDefault())

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        hideSystemUi()
        buildHome()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            timeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        )
        refreshClock()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(timeReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun onResume() {
        super.onResume()
        // An app may have been installed or removed while the launcher was in the background.
        updateSelector(false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Pressing Home while already on the launcher returns to the first tile.
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            settingsFocused = false
            applySettingsFocus()
            selected = 0
            updateSelector(true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Deprecated("Back does nothing on a home screen.")
    override fun onBackPressed() {
        // Intentionally empty: this is the Home app.
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                setSettingsFocus(true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                setSettingsFocus(false)
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
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (event?.repeatCount == 0) {
                    if (settingsFocused) openSettings() else launchSelected()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                if (event?.repeatCount == 0) openSettings()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------------------------------------------------------- layout

    private fun buildHome() {
        val root = FrameLayout(this).apply {
            clipChildren = false
            isFocusableInTouchMode = true
        }

        backdrop = Backdrop(this)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        // Top-left: brand (doubles as the Settings button).
        brand = TextView(this).apply {
            text = "A.U.R.I.X"
            setTextColor(Color.WHITE)
            textSize = 22f
            letterSpacing = 0.08f
            typeface = customFont()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            contentDescription = "A.U.R.I.X Settings"
            setOnClickListener { openSettings() }
        }
        root.addView(
            brand,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(50), dp(30), 0, 0)
            }
        )
        settingsHint = TextView(this).apply {
            text = "Press OK for Settings"
            textSize = 12f
            setTextColor(Color.argb(170, 255, 255, 255))
            typeface = customFont()
            letterSpacing = 0.04f
            visibility = View.INVISIBLE
        }
        root.addView(
            settingsHint,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(64), dp(86), 0, 0)
            }
        )

        // Top-right: clock and date.
        clock = TextView(this).apply {
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = customFont()
            includeFontPadding = false
        }
        date = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.argb(190, 255, 255, 255))
            typeface = customFont()
            includeFontPadding = false
        }
        val timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(clock)
            addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        }
        root.addView(
            timeBox,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(30), dp(56), 0)
            }
        )

        // Hero: the selected app's name and state.
        heroTag = TextView(this).apply {
            textSize = 13f
            letterSpacing = 0.2f
            typeface = customFont()
        }
        heroName = TextView(this).apply {
            textSize = 58f
            setTextColor(Color.WHITE)
            typeface = customFont()
            includeFontPadding = false
        }
        heroStatus = TextView(this).apply {
            textSize = 15f
            typeface = customFont()
        }
        hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heroTag)
            addView(heroName, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
            addView(heroStatus, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        }
        root.addView(
            hero,
            FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                setMargins(dp(64), 0, 0, dp(110))
            }
        )

        // Bottom: the tile row.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        apps.forEachIndexed { index, app ->
            val tile = FrameLayout(this).apply {
                background = tileBackground(false)
                isClickable = true
                contentDescription = app.name
                setOnClickListener {
                    selected = index
                    setSettingsFocus(false)
                    updateSelector(true)
                    launchSelected()
                }
            }
            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (app.logo != null) setImageResource(app.logo) else setImageDrawable(GlobeDrawable())
            }
            tile.addView(
                icon,
                FrameLayout.LayoutParams(dp(62), dp(62), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = dp(22)
                }
            )
            val label = TextView(this).apply {
                text = app.name
                textSize = 14f
                setTextColor(Color.WHITE)
                letterSpacing = 0.04f
                typeface = customFont()
            }
            tile.addView(
                label,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dp(16)
                }
            )
            row.addView(tile, LinearLayout.LayoutParams(dp(190), dp(132)).apply {
                setMargins(dp(14), 0, dp(14), 0)
            })
            tiles.add(tile)
        }
        root.addView(
            row,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(64)
            }
        )

        setContentView(root)
        root.requestFocus()
        refreshClock()
        applySettingsFocus()
        updateSelector(false)
    }

    // ----------------------------------------------------------------- state

    private fun changeSelection(direction: Int) {
        val next = (selected + direction).coerceIn(0, apps.lastIndex)
        if (next == selected) return
        selected = next
        updateSelector(true)
    }

    private fun setSettingsFocus(focused: Boolean) {
        if (settingsFocused == focused) return
        settingsFocused = focused
        applySettingsFocus()
        updateSelector(true)
    }

    private fun applySettingsFocus() {
        brand.background = if (settingsFocused) GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(46, 255, 255, 255))
            setStroke(dp(2), Color.WHITE)
        } else null
        settingsHint.visibility = if (settingsFocused) View.VISIBLE else View.INVISIBLE
    }

    private fun updateSelector(animate: Boolean) {
        val app = apps[selected]

        tiles.forEachIndexed { index, tile ->
            val isSelected = index == selected
            val active = isSelected && !settingsFocused
            val installed = isInstalled(apps[index])

            tile.background = tileBackground(active)

            val scale = if (isSelected) 1.1f else 1f
            val alpha = when {
                !installed -> 0.38f
                isSelected -> 1f
                else -> 0.78f
            }
            if (animate) {
                tile.animate().scaleX(scale).scaleY(scale).alpha(alpha)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                tile.scaleX = scale
                tile.scaleY = scale
                tile.alpha = alpha
            }
        }

        updateHero(app, animate)
        animateAccent(app.accent, animate)
    }

    private fun updateHero(app: AppTile, animate: Boolean) {
        val installed = isInstalled(app)
        heroTag.text = app.tagline.uppercase(Locale.ROOT)
        heroTag.setTextColor(mix(app.accent, Color.WHITE, 0.35f))
        heroName.text = app.name
        // Only shown when the app is missing.
        heroStatus.text = "Not installed"
        heroStatus.setTextColor(Color.rgb(255, 193, 7))
        heroStatus.visibility = if (installed) View.GONE else View.VISIBLE
        if (animate) {
            hero.alpha = 0f
            hero.translationX = -dp(16).toFloat()
            hero.animate().alpha(1f).translationX(0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            hero.alpha = 1f
            hero.translationX = 0f
        }
    }

    private fun animateAccent(target: Int, animate: Boolean) {
        accentAnimator?.cancel()
        if (!animate) {
            backdrop.accent = target
            return
        }
        accentAnimator = ValueAnimator.ofObject(ArgbEvaluator(), backdrop.accent, target).apply {
            duration = 380
            addUpdateListener { backdrop.accent = it.animatedValue as Int }
            start()
        }
    }

    private fun isInstalled(app: AppTile): Boolean =
        packageManager.getLaunchIntentForPackage(app.packageName) != null

    private fun launchSelected() {
        val app = apps[selected]
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "${app.name} is not installed.", Toast.LENGTH_SHORT).show()
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
                startActivity(
                    Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings")
                )
            } catch (_: Exception) {
                Toast.makeText(this, "TV Settings could not be opened.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshClock() {
        if (!::clock.isInitialized) return
        val now = Date()
        clock.text = clockFormat.format(now)
        date.text = dateFormat.format(now)
    }

    // --------------------------------------------------------------- helpers

    // Frosted-glass card: translucent white fill, a soft sheen across the top half
    // and a bright hairline edge. (True background blur needs API 31+, so the
    // glow in Backdrop shows through the translucent fill instead.)
    private fun tileBackground(active: Boolean): android.graphics.drawable.Drawable {
        val radius = dp(22).toFloat()
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (active) intArrayOf(Color.argb(105, 255, 255, 255), Color.argb(38, 255, 255, 255))
            else intArrayOf(Color.argb(54, 255, 255, 255), Color.argb(16, 255, 255, 255))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            if (active) setStroke(dp(2), Color.argb(235, 255, 255, 255))
            else setStroke(dp(1), Color.argb(96, 255, 255, 255))
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(if (active) 70 else 44, 255, 255, 255), Color.TRANSPARENT)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius - dp(2)
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(base, sheen)).apply {
            setLayerInset(1, dp(2), dp(2), dp(2), dp(66))
        }
    }

    private fun mix(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
    )

    private fun customFont(): Typeface = try {
        if (Build.VERSION.SDK_INT >= 26) resources.getFont(R.font.arial_black)
        else Typeface.create("sans-serif-black", Typeface.NORMAL)
    } catch (_: Exception) {
        Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}

/** Dark vertical gradient with a soft glow in the selected app's accent colour. */
private class Backdrop(context: Context) : View(context) {
    private val basePaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var accent: Int = Color.WHITE
        set(value) {
            field = value
            rebuildGlow()
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        basePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.rgb(13, 20, 32), Color.rgb(4, 6, 10), Shader.TileMode.CLAMP
        )
        rebuildGlow()
    }

    private fun rebuildGlow() {
        if (width == 0 || height == 0) return
        glowPaint.shader = RadialGradient(
            width * 0.2f, height * 0.34f, width * 0.65f,
            Color.argb(92, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
    }
}

/** Simple white globe, used for the Browser tile so no brand artwork is needed. */
private class GlobeDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    override fun getIntrinsicWidth() = 256
    override fun getIntrinsicHeight() = 256

    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f * 0.88f
        paint.strokeWidth = r * 0.1f
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawLine(cx - r, cy, cx + r, cy, paint)
        canvas.drawOval(RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), paint)
        val y = r * 0.5f
        val half = Math.sqrt((r * r - y * y).toDouble()).toFloat()
        canvas.drawLine(cx - half, cy - y, cx + half, cy - y, paint)
        canvas.drawLine(cx - half, cy + y, cx + half, cy + y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
