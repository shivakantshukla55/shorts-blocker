package com.example.shortsblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.accessibilityservice.AccessibilityServiceInfo

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var container: LinearLayout
    private lateinit var capStatus: TextView

    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        setContentView(android.widget.ScrollView(this).apply { addView(container) })
        build()
    }

    override fun onResume() {
        super.onResume()
        build()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 16, 0, 16)
    }

    private fun pwField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun build() {
        container.removeAllViews()
        container.addView(tv("Shorts Blocker", 26f, true))
        status = tv("", 16f, true)
        container.addView(status)

        container.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        if (!Prefs.hasPassword(this)) buildSetPassword() else buildPauseControls()

        buildDiagnostic()

        container.addView(
            tv(
                "Turn on \"Shorts Blocker\" under Installed apps in Accessibility settings. " +
                    "If the switch is greyed out: Settings > Apps > Shorts Blocker > ⋮ menu > " +
                    "Allow restricted settings.",
                13f
            ).apply { setTextColor(Color.DKGRAY) }
        )
        refreshStatus()
    }

    private fun buildSetPassword() {
        container.addView(tv("Create your unlock password", 18f, true))
        container.addView(
            tv("At least ${Prefs.MIN_LENGTH} characters with upper and lower case, a digit and a symbol. " +
                "Store it somewhere that is not convenient, or give it to someone you trust.", 14f)
        )
        val p1 = pwField("Password")
        val p2 = pwField("Repeat password")
        container.addView(p1)
        container.addView(p2)
        container.addView(Button(this).apply {
            text = "Save password"
            setOnClickListener {
                val a = p1.text.toString()
                val b = p2.text.toString()
                val problem = Prefs.validateStrength(a)
                when {
                    problem != null -> toast(problem)
                    a != b -> toast("Passwords do not match.")
                    else -> {
                        Prefs.setPassword(this@MainActivity, a)
                        toast("Password saved.")
                        build()
                    }
                }
            }
        })
    }

    private fun buildPauseControls() {
        container.addView(tv("Pause blocking for 5 minutes", 18f, true))
        val pw = pwField("Enter password")
        container.addView(pw)
        container.addView(Button(this).apply {
            text = "Unlock for 5 minutes"
            setOnClickListener {
                val locked = Prefs.lockedForMs(this@MainActivity)
                if (locked > 0) {
                    toast("Too many wrong attempts. Wait ${locked / 1000}s.")
                    return@setOnClickListener
                }
                if (Prefs.checkPassword(this@MainActivity, pw.text.toString())) {
                    Prefs.startPause(this@MainActivity)
                    pw.setText("")
                    toast("Blocking paused for 5 minutes.")
                    refreshStatus()
                } else {
                    val l = Prefs.lockedForMs(this@MainActivity)
                    toast(if (l > 0) "Wrong password. Locked for ${l / 1000}s." else "Wrong password.")
                }
            }
        })
        container.addView(Button(this).apply {
            text = "Resume blocking now"
            setOnClickListener {
                Prefs.endPause(this@MainActivity)
                refreshStatus()
            }
        })
    }

    private fun buildDiagnostic() {
        container.addView(tv("Diagnostic: Instagram Explore preview", 18f, true))
        container.addView(
            tv(
                "1) Tap Capture A, open Instagram > Explore and scroll around (do not hold anything). " +
                    "2) Come back here, tap Capture B, open Explore and press-and-hold a reel for a few seconds. " +
                    "3) Come back and tap Show result. Only view class names and ids are recorded, never text. " +
                    "Blocking on Instagram is switched off while a capture runs.",
                13f
            )
        )
        capStatus = tv("", 14f)
        container.addView(capStatus)

        container.addView(Button(this).apply {
            text = "Capture A (normal Explore)"
            setOnClickListener {
                Capture.start(1)
                toast("Capturing A for 30s. Open Instagram now.")
            }
        })
        container.addView(Button(this).apply {
            text = "Capture B (holding a reel)"
            setOnClickListener {
                Capture.start(2)
                toast("Capturing B for 30s. Open Instagram and hold a reel.")
            }
        })

        val result = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        container.addView(Button(this).apply {
            text = "Show result"
            setOnClickListener { result.text = Capture.report() }
        })
        container.addView(Button(this).apply {
            text = "Share result"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, Capture.report())
                }
                startActivity(Intent.createChooser(send, "Share result"))
            }
        })
        container.addView(result)
    }

    private fun serviceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        if (::capStatus.isInitialized) {
            capStatus.text = if (Capture.active()) {
                val which = if (Capture.mode == 1) "A" else "B"
                "Capturing $which: ${Capture.leftMs() / 1000}s left. Go to Instagram now."
            } else {
                "Capture idle."
            }
        }
        val left = Prefs.pauseLeftMs(this)
        when {
            !serviceEnabled() -> {
                status.text = "Not active: enable the accessibility service."
                status.setTextColor(Color.rgb(200, 0, 0))
            }
            left > 0 -> {
                val s = left / 1000
                status.text = "Paused: %d:%02d left".format(s / 60, s % 60)
                status.setTextColor(Color.rgb(200, 120, 0))
            }
            else -> {
                status.text = "Active: Shorts and Reels are blocked."
                status.setTextColor(Color.rgb(0, 140, 0))
            }
        }
        status.gravity = Gravity.START
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
