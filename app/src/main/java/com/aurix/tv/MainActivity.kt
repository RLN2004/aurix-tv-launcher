package com.aurix.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(11, 16, 24)
    private val muted = Color.rgb(151, 165, 181)
    private val white = Color.rgb(242, 246, 250)
    private lateinit var clock: TextView
    private val clockFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = Color.rgb(8, 12, 18)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildHome()
    }

    override fun onResume() {
        super.onResume()
        if (::clock.isInitialized) refreshClock()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(54), dp(30), dp(54), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 29, 43), bg, Color.rgb(8, 13, 20))
            )
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandColumn.addView(label("A.U.R.I.X.", 25, white, true))
        brandColumn.addView(label("YOUR TV. NOTHING EXTRA.", 10, muted, true).apply {
            letterSpacing = 0.16f
            setPadding(1, dp(3), 0, 0)
        })
        top.addView(brandColumn, LinearLayout.LayoutParams(0, -2, 1f))

        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clock = label("", 27, white, true)
        timeColumn.addView(clock)
        timeColumn.addView(label(dateFormat.format(Date()), 11, muted, false))
        top.addView(timeColumn)
        root.addView(top)

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(36), 0, dp(18))
        }
        section.addView(label("Good to see you.", 24, white, true))
        section.addView(label("Choose what you want to watch.", 13, muted, false).apply {
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(section)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val tiles = listOf(
            Tile("TV / HDMI", "INPUT", "com.android.tv", R.drawable.tile_tv, "TV"),
            Tile("YouTube", "VIDEO", "com.google.android.youtube.tv", R.drawable.tile_youtube, "YT"),
            Tile("Hotstar", "STREAMING", "in.startv.hotstar", R.drawable.tile_hotstar, "H"),
            Tile("Netflix", "STREAMING", "com.netflix.ninja", R.drawable.tile_netflix, "N")
        )
        tiles.forEachIndexed { index, tile ->
            val view = makeTile(tile)
            val lp = LinearLayout.LayoutParams(0, dp(176), 1f)
            if (index > 0) lp.marginStart = dp(14)
            row.addView(view, lp)
        }
        root.addView(row)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, 0)
        }
        footer.addView(label("Use the remote to select an app", 11, muted, false), LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(label("A.U.R.I.X. TV  •  v1.0", 10, muted, false))
        root.addView(footer)

        setContentView(root)
        refreshClock()
        row.getChildAt(0).requestFocus()
    }

    private data class Tile(
        val title: String,
        val subtitle: String,
        val packageName: String,
        val backgroundRes: Int,
        val mark: String
    )

    private fun makeTile(tile: Tile): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(12), dp(15), dp(12), dp(14))
            background = getDrawable(tile.backgroundRes)
            setOnFocusChangeListener { v, focused ->
                v.animate().scaleX(if (focused) 1.045f else 1f)
                    .scaleY(if (focused) 1.045f else 1f)
                    .setDuration(130).start()
                v.alpha = if (focused) 1f else 0.88f
                v.foreground = if (focused) roundedBorder(Color.rgb(220, 239, 244)) else null
            }
            setOnClickListener { launchTile(tile) }
        }
        val mark = TextView(this).apply {
            text = tile.mark
            textSize = 30f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(white)
            gravity = Gravity.CENTER
        }
        card.addView(mark, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addView(label(tile.title, 17, white, true).apply { gravity = Gravity.CENTER })
        card.addView(label(tile.subtitle, 9, Color.rgb(190, 202, 215), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setPadding(0, dp(5), 0, 0)
        })
        return card
    }

    private fun launchTile(tile: Tile) {
        val intent = packageManager.getLaunchIntentForPackage(tile.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "${tile.title} could not be opened.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "${tile.title} is not installed or is disabled.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshClock() {
        clock.text = clockFormat.format(Date())
        // Clock refreshes whenever the launcher resumes; no timer/service is kept alive.
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                       else Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
        }

    private fun roundedBorder(color: Int): android.graphics.drawable.Drawable =
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(22).toFloat()
            setStroke(dp(2), color)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
