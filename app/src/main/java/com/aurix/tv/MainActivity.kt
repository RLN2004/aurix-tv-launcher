package com.aurix.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
        val packageName: String,
        val icon: IconKind
    )

    private val apps = listOf(
        AppTile("YouTube", "com.google.android.youtube.tv", IconKind.PLAY),
        AppTile("TV", "com.android.tv", IconKind.TV),
        AppTile("Netflix", "com.netflix.ninja", IconKind.FILM),
        AppTile("Browser", "com.nitesh.rhenium", IconKind.GLOBE)
    )

    private lateinit var brand: TextView
    private lateinit var brandSlot: FrameLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var hero: LinearLayout
    private lateinit var heroName: TextView
    private lateinit var heroStatus: TextView
    private val slots = mutableListOf<FrameLayout>()
    private val cards = mutableListOf<FrameLayout>()

    private var selected = 0
    private var settingsFocused = false

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
            setBackgroundColor(Color.BLACK)
            clipChildren = false
            isFocusableInTouchMode = true
        }

        // Top-left: brand (doubles as the Settings button). It sits in a slot so the
        // white glow has room to draw around it.
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
        brandSlot = FrameLayout(this).apply {
            clipChildren = false
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(brand, FrameLayout.LayoutParams(-2, -2))
        }
        root.addView(
            brandSlot,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(34), dp(14), 0, 0)
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

        // Hero: the selected app's name (and a warning if it is not installed).
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
            addView(heroName)
            addView(heroStatus, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        }
        root.addView(
            hero,
            FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                setMargins(dp(64), 0, 0, dp(110))
            }
        )

        // Bottom: the tile row. Each card sits in a slot so its glow has room to draw.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        apps.forEachIndexed { index, app ->
            val card = FrameLayout(this).apply {
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
                setImageDrawable(LineIcon(app.icon))
            }
            card.addView(
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
            card.addView(
                label,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dp(16)
                }
            )

            val slot = FrameLayout(this).apply {
                clipChildren = false
                setPadding(dp(GLOW_PAD), dp(GLOW_PAD), dp(GLOW_PAD), dp(GLOW_PAD))
                addView(card, FrameLayout.LayoutParams(dp(190), dp(132)))
            }
            row.addView(slot, LinearLayout.LayoutParams(-2, -2))
            slots.add(slot)
            cards.add(card)
        }
        root.addView(
            row,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(46)
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
        if (settingsFocused) {
            // Text glow only: no box or ring around the label.
            brand.setShadowLayer(dp(16).toFloat(), 0f, 0f, Color.WHITE)
        } else {
            brand.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    private fun updateSelector(animate: Boolean) {
        val app = apps[selected]

        slots.forEachIndexed { index, slot ->
            val isSelected = index == selected
            val active = isSelected && !settingsFocused
            val installed = isInstalled(apps[index])

            cards[index].background = tileBackground(active)
            slot.background = if (active) glowDrawable(dp(22).toFloat(), GLOW_PAD, 5) else null

            val scale = if (isSelected) 1.08f else 1f
            val alpha = when {
                !installed -> 0.38f
                isSelected -> 1f
                else -> 0.8f
            }
            if (animate) {
                slot.animate().scaleX(scale).scaleY(scale).alpha(alpha)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                slot.scaleX = scale
                slot.scaleY = scale
                slot.alpha = alpha
            }
        }

        updateHero(app, animate)
    }

    private fun updateHero(app: AppTile, animate: Boolean) {
        val installed = isInstalled(app)
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
    // and a bright hairline edge. The fill is identical whether or not the card is
    // active; only the border (and the glow outside it) changes. (True background blur needs API 31+; on a black
    // background the translucent fill reads as glass.)
    private fun tileBackground(active: Boolean): Drawable {
        val radius = dp(22).toFloat()
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(54, 255, 255, 255), Color.argb(16, 255, 255, 255))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            if (active) setStroke(dp(2), Color.argb(240, 255, 255, 255))
            else setStroke(dp(1), Color.argb(96, 255, 255, 255))
        }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(44, 255, 255, 255), Color.TRANSPARENT)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius - dp(2)
        }
        return LayerDrawable(arrayOf(base, sheen)).apply {
            setLayerInset(1, dp(2), dp(2), dp(2), dp(66))
        }
    }

    /**
     * A soft white glow made of concentric rounded outlines that fade outwards. It fills a
     * slot whose padding is [padDp], so the glow hugs the edge of the view inside it.
     */
    private fun glowDrawable(innerRadius: Float, padDp: Int, steps: Int): Drawable {
        val ring = dp(3)
        val pad = dp(padDp)
        val layers = Array<Drawable>(steps) { j ->
            val inset = pad - ring / 2 - ring * (steps - 1 - j)
            val fade = (j + 1f) / steps
            val alpha = (125 * fade * fade).toInt()
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                cornerRadius = innerRadius + (pad - inset)
                setStroke(ring, Color.argb(alpha, 255, 255, 255))
            }
        }
        return LayerDrawable(layers).apply {
            for (j in 0 until steps) {
                val inset = pad - ring / 2 - ring * (steps - 1 - j)
                setLayerInset(j, inset, inset, inset, inset)
            }
        }
    }

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

    private companion object {
        const val GLOW_PAD = 18
    }
}

private enum class IconKind { PLAY, TV, FILM, GLOBE }

/** Simple white line-art icons (no brand artwork), drawn on a square unit box. */
private class LineIcon(private val kind: IconKind) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun getIntrinsicWidth() = 256
    override fun getIntrinsicHeight() = 256

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val left = b.exactCenterX() - s / 2f
        val top = b.exactCenterY() - s / 2f
        fun x(f: Float) = left + s * f
        fun y(f: Float) = top + s * f
        fun rect(l: Float, t: Float, r: Float, bt: Float) = RectF(x(l), y(t), x(r), y(bt))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            canvas.drawLine(x(x1), y(y1), x(x2), y(y2), paint)

        paint.strokeWidth = s * 0.045f
        when (kind) {
            IconKind.PLAY -> {
                canvas.drawRoundRect(rect(.06f, .20f, .94f, .80f), s * .16f, s * .16f, paint)
                val tri = Path().apply {
                    moveTo(x(.42f), y(.36f))
                    lineTo(x(.42f), y(.64f))
                    lineTo(x(.65f), y(.50f))
                    close()
                }
                canvas.drawPath(tri, paint)
            }
            IconKind.TV -> {
                canvas.drawRoundRect(rect(.06f, .14f, .94f, .70f), s * .08f, s * .08f, paint)
                line(.50f, .70f, .50f, .84f)
                line(.30f, .86f, .70f, .86f)
            }
            IconKind.FILM -> {
                canvas.drawRoundRect(rect(.10f, .38f, .90f, .86f), s * .06f, s * .06f, paint)
                canvas.drawRoundRect(rect(.10f, .18f, .90f, .38f), s * .04f, s * .04f, paint)
                line(.30f, .18f, .22f, .38f)
                line(.52f, .18f, .44f, .38f)
                line(.74f, .18f, .66f, .38f)
            }
            IconKind.GLOBE -> {
                val c = .50f
                val r = s * .44f
                canvas.drawCircle(x(c), y(c), r, paint)
                line(.06f, .50f, .94f, .50f)
                canvas.drawOval(rect(.30f, .06f, .70f, .94f), paint)
                line(.14f, .28f, .86f, .28f)
                line(.14f, .72f, .86f, .72f)
            }
        }
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
