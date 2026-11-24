package com.gt7.telemetry

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class MainActivity : Activity() {
    private val tracker = LapTracker()
    private lateinit var dash: DashboardView
    private lateinit var panel: LinearLayout
    private lateinit var ipField: EditText
    private lateinit var guide: ScrollView
    private var client: Gt7Client? = null
    private val ui = Handler(Looper.getMainLooper())
    private var running = false
    private var demoOn = false
    private var demoStartNs = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (demoOn) feedDemo()
            dash.invalidate()
            ui.postDelayed(this, 33)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) 0xFF0B0D10.toInt() else 0xFFF5F5F2.toInt()
        val fg = if (night) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
        val sub = if (night) 0xFF8A9099.toInt() else 0xFF666666.toInt()

        val root = FrameLayout(this)
        root.setBackgroundColor(bg)
        dash = DashboardView(this, tracker)
        dash.visibility = View.GONE
        dash.setOnLongClickListener { stopAll(); true }
        root.addView(dash, FrameLayout.LayoutParams(-1, -1))

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.gravity = Gravity.CENTER
        panel.setPadding(64, 24, 64, 24)

        val title = TextView(this)
        title.text = "GT7 Telemetry"
        title.textSize = 26f
        title.setTextColor(fg)
        title.gravity = Gravity.CENTER
        panel.addView(title)

        val hint = TextView(this)
        hint.text = "Enter your PS5 IP address (Settings > Network > Connection Status). Phone and PS5 must be on the same network."
        hint.textSize = 14f
        hint.setTextColor(sub)
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, 8, 0, 16)
        panel.addView(hint)

        ipField = EditText(this)
        ipField.hint = "192.168.1.50"
        ipField.inputType = InputType.TYPE_CLASS_PHONE
        ipField.setTextColor(fg)
        ipField.setHintTextColor(sub)
        ipField.setText(getSharedPreferences("gt7", MODE_PRIVATE).getString("ip", "") ?: "")
        panel.addView(ipField, LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val connect = Button(this)
        connect.text = "Connect"
        connect.setOnClickListener { connect() }
        val demo = Button(this)
        demo.text = "Demo mode"
        demo.setOnClickListener { startDemo() }
        val help = Button(this)
        help.text = "How to set up"
        help.setOnClickListener { showGuide(true) }
        row.addView(connect)
        row.addView(demo)
        row.addView(help)
        panel.addView(row)

        val foot = TextView(this)
        foot.text = "Long-press the dashboard to go back."
        foot.textSize = 12f
        foot.setTextColor(sub)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, 16, 0, 0)
        panel.addView(foot)

        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        guide = buildGuide(bg, fg, sub)
        guide.visibility = View.GONE
        root.addView(guide, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        if (!getSharedPreferences("gt7", MODE_PRIVATE).getBoolean("seen_guide", false)) {
            getSharedPreferences("gt7", MODE_PRIVATE).edit().putBoolean("seen_guide", true).apply()
            showGuide(true)
        }
    }

    private fun showGuide(show: Boolean) {
        guide.visibility = if (show) View.VISIBLE else View.GONE
        panel.visibility = if (show) View.GONE else View.VISIBLE
        if (show) guide.scrollTo(0, 0)
    }

    private fun buildGuide(bg: Int, fg: Int, sub: Int): ScrollView {
        val sv = ScrollView(this)
        sv.setBackgroundColor(bg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(64, 32, 64, 48)

        fun head(text: String) {
            val t = TextView(this)
            t.text = text
            t.textSize = 18f
            t.setTextColor(fg)
            t.setTypeface(null, android.graphics.Typeface.BOLD)
            t.setPadding(0, 28, 0, 6)
            col.addView(t)
        }
        fun body(text: String) {
            val t = TextView(this)
            t.text = text
            t.textSize = 15f
            t.setTextColor(sub)
            t.setLineSpacing(0f, 1.2f)
            col.addView(t)
        }

        val title = TextView(this)
        title.text = "How to set up GT7 Telemetry"
        title.textSize = 24f
        title.setTextColor(fg)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        col.addView(title)

        head("1. Put the phone and PS5 on the same network")
        body("Connect your phone to the same Wi-Fi or router as the PS5. The PS5 can be on a cable. " +
            "Don't use mobile data, a guest network, or a VPN on the phone, because those block the connection.")

        head("2. Find your PS5's IP address")
        body("On the PS5, open Settings > Network > Connection Status > View Connection Status, " +
            "then note the IP address, for example 192.168.1.50.\n\n" +
            "Tip: the address can change after a restart. If it does, update it in the app, " +
            "or reserve a fixed address for the PS5 in your router settings.")

        head("3. Connect")
        body("Type the IP address into the box on the main screen and tap Connect. " +
            "The app remembers it for next time.")

        head("4. Start driving in GT7")
        body("Telemetry is only sent while you're on track. Start a race, a time trial, or Free Run. " +
            "Menus, replays, and photo mode send nothing, so the app will say it's waiting.")

        head("5. Reading the dashboard")
        body("Shift lights: fill left to right as RPM rises, and all flash red at the limiter.\n" +
            "Pedals: green is throttle, red is brake.\n" +
            "Gear: turns amber when GT7 suggests a different gear.\n" +
            "Tyres: blue is cold, green is good, amber is hot, red is too hot (about 60, 95, and 110 degrees C).\n" +
            "Fuel: percent left, plus laps remaining once you've finished a lap.\n" +
            "Delta to best lap: green means you're faster than your best lap, red means slower. " +
            "It appears after you've set a best lap.")

        head("6. If it says \"Waiting for PS5\"")
        body("• Check the IP address is correct and the PS5 is on, with GT7 running.\n" +
            "• Make sure you are on track, not in a menu.\n" +
            "• Confirm the phone is on the same Wi-Fi, and turn off any VPN.\n" +
            "• Some routers isolate devices (AP or client isolation). Turn that off, or use a different network.\n" +
            "• Tap back, then Connect again, or restart GT7.")

        head("Good to know")
        body("GT7 only shares data about your own car. It does not provide positions or gaps to other drivers.\n\n" +
            "No PS5 handy? Tap Demo mode to see the dashboard with sample data.\n\n" +
            "Long-press the dashboard, or press Back, to return to this screen.")

        val back = Button(this)
        back.text = "Got it"
        back.setOnClickListener { showGuide(false) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 32
        col.addView(back, lp)

        sv.addView(col)
        return sv
    }

    private fun connect() {
        val ip = ipField.text.toString().trim()
        if (ip.isEmpty()) {
            ipField.error = "Enter the PS5 IP address"
            return
        }
        getSharedPreferences("gt7", MODE_PRIVATE).edit().putString("ip", ip).apply()
        demoOn = false
        showDash()
        dash.statusText = "Connecting…"
        client = Gt7Client(
            ip,
            { t ->
                tracker.update(t, System.nanoTime())
                dash.data = t
                dash.lastPacketNs = System.nanoTime()
            },
            { s -> dash.statusText = s },
        ).also { it.start() }
    }

    private fun startDemo() {
        demoOn = true
        demoStartNs = System.nanoTime()
        showDash()
    }

    private fun showDash() {
        tracker.reset()
        dash.data = null
        panel.visibility = View.GONE
        dash.visibility = View.VISIBLE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        running = true
        ui.post(tick)
    }

    private fun stopAll() {
        running = false
        demoOn = false
        client?.stop()
        client = null
        dash.visibility = View.GONE
        panel.visibility = View.VISIBLE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (guide.visibility == View.VISIBLE) showGuide(false)
        else if (running) stopAll() else super.onBackPressed()
    }

    override fun onDestroy() {
        client?.stop()
        super.onDestroy()
    }

    /** Synthetic 60-second laps so the layout, shift lights and delta can be tried without a PS5. */
    private fun feedDemo() {
        val now = System.nanoTime()
        val t = (now - demoStartNs) / 1e9
        val lapLen = 60.0
        val lap = 1 + (t / lapLen).toInt()
        val scale = 1.0 + 0.03 * sin(lap * 2.3)
        val ph = (t % lapLen) / lapLen
        val speedMs = ((45.0 + 28.0 * sin(2 * PI * ph * 3)) * scale).toFloat()
        val rpmFrac = 0.5 + 0.5 * sin(2 * PI * t / 5.0)
        val rpm = (3500 + 4300 * rpmFrac).toFloat()
        val gear = (2 + (speedMs / 14f).toInt()).coerceIn(1, 6)
        val thr = max(0.0, sin(2 * PI * t / 5.0 + 0.6)).toFloat()
        val brk = max(0.0, -sin(2 * PI * t / 5.0 + 0.6)).toFloat() * 0.8f
        var best = -1
        var last = -1
        for (l in 1 until lap) {
            last = 60000 + ((l * 7919) % 2000) - 1000 + ((scale - 1.0) * -20000).toInt()
            best = if (best < 0) last else minOf(best, last)
        }
        val fuel = max(5.0, 100.0 - t * 0.12).toFloat()
        val tyres = FloatArray(4) { i -> (82 + 18 * sin(t / 7.0 + i)).toFloat() }
        val p = Telemetry(
            rpm = rpm, speedMs = speedMs, gear = gear, throttle = thr, brake = brk,
            fuel = fuel, fuelCap = 100f, tyres = tyres, lap = lap, laps = 5,
            bestMs = best, lastMs = last, flags = 1, minAlertRpm = 6500, maxAlertRpm = 7800,
        )
        tracker.update(p, now)
        dash.data = p
        dash.lastPacketNs = now
    }
}
