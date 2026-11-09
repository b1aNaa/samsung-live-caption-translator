package com.livesub.translate

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import java.util.Locale
import com.livesub.translate.Prefs.baiduAppId
import com.livesub.translate.Prefs.baiduKey
import com.livesub.translate.Prefs.bgAlpha
import com.livesub.translate.Prefs.dark
import com.livesub.translate.Prefs.deepseekKey
import com.livesub.translate.Prefs.engine
import com.livesub.translate.Prefs.fontSp
import com.livesub.translate.Prefs.lang
import com.livesub.translate.Prefs.lastLines
import com.livesub.translate.Prefs.qwenKey
import com.livesub.translate.Prefs.showSrc

class MainActivity : Activity() {

    companion object {
        const val ACTION_STATE = "com.livesub.translate.STATE"
        const val EXTRA_RUNNING = "running"
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvEngineDesc: TextView
    private lateinit var tvA11y: TextView
    private lateinit var tvLastLines: TextView
    private lateinit var spEngine: Spinner
    private lateinit var baiduBox: LinearLayout
    private lateinit var apiKeyBox: LinearLayout
    private lateinit var etBaiduId: EditText
    private lateinit var etBaiduKey: EditText
    private lateinit var etApiKey: EditText
    private lateinit var tvKeyHint: TextView
    private var keyEngine = "qwen"
    private var fieldsReady = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateStatus()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val l = newBase.lang
        if (l.isEmpty()) {
            super.attachBaseContext(newBase)
            return
        }
        val cfg = Configuration(newBase.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(l))
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (dark) applyDarkMode(window.decorView)

        drawer = findViewById(R.id.drawer)
        tvStatus = findViewById(R.id.tvStatus)
        tvEngineDesc = findViewById(R.id.tvEngineDesc)
        tvA11y = findViewById(R.id.tvA11y)
        tvLastLines = findViewById(R.id.tvLastLines)
        spEngine = findViewById(R.id.spEngine)
        baiduBox = findViewById(R.id.baiduBox)
        apiKeyBox = findViewById(R.id.apiKeyBox)
        etBaiduId = findViewById(R.id.etBaiduId)
        etBaiduKey = findViewById(R.id.etBaiduKey)
        etApiKey = findViewById(R.id.etApiKey)
        tvKeyHint = findViewById(R.id.tvKeyHint)

        setupSpinners()
        setupButtons()
        setupSeekBars()

        etBaiduId.setText(baiduAppId)
        etBaiduKey.setText(baiduKey)
        etBaiduId.setOnFocusChangeListener { _, _ -> baiduAppId = etBaiduId.text.toString().trim() }
        etBaiduKey.setOnFocusChangeListener { _, _ -> baiduKey = etBaiduKey.text.toString().trim() }

        findViewById<CheckBox>(R.id.cbShowSrc).apply {
            isChecked = showSrc
            setOnCheckedChangeListener { _, checked -> showSrc = checked }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        val filter = IntentFilter(ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        baiduAppId = etBaiduId.text.toString().trim()
        baiduKey = etBaiduKey.text.toString().trim()
        saveApiKey()
    }

    private fun setupSpinners() {
        spEngine.adapter = makeAdapter(R.array.engine_names)
        spEngine.setSelection(indexOf(R.array.engine_values, engine))
        spEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                saveApiKey()
                engine = resources.getStringArray(R.array.engine_values)[pos]
                showKeyFields()
                tvEngineDesc.text = getString(
                    when (engine) {
                        "baidu" -> R.string.engine_desc_baidu
                        "deepseek" -> R.string.engine_desc_deepseek
                        else -> R.string.engine_desc_qwen
                    }
                )
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        showKeyFields()
        spEngine.setSelection(indexOf(R.array.engine_values, engine))
    }

    /** Persist the API-key field to whichever engine it belongs to. */
    private fun saveApiKey() {
        if (!fieldsReady) return
        val v = etApiKey.text.toString().trim()
        when (keyEngine) {
            "deepseek" -> deepseekKey = v
            else -> qwenKey = v
        }
    }

    private fun showKeyFields() {
        val isBaidu = engine == "baidu"
        baiduBox.visibility = if (isBaidu) View.VISIBLE else View.GONE
        apiKeyBox.visibility = if (isBaidu) View.GONE else View.VISIBLE
        tvKeyHint.text = getString(
            if (engine == "deepseek") R.string.key_hint_deepseek
            else R.string.key_hint_qwen
        )
        etApiKey.setText(if (engine == "deepseek") deepseekKey else qwenKey)
        keyEngine = engine
        fieldsReady = true
    }

    private fun makeAdapter(arrayRes: Int): ArrayAdapter<CharSequence> {
        val items = resources.getStringArray(arrayRes)
        return object : ArrayAdapter<CharSequence>(
            this, android.R.layout.simple_spinner_item, items
        ) {
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val view = super.getView(p, v, parent)
                if (dark && view is TextView) {
                    view.setTextColor(0xFFE8EAED.toInt())
                }
                return view
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    /** Recolor the inflated tree for dark mode (called once after setContentView). */
    private fun applyDarkMode(root: View) {
        val textMap = mapOf(
            0xFF21293B.toInt() to 0xFFE8EAED.toInt(),  // text_primary
            0xFF5A6474.toInt() to 0xFF9AA3B2.toInt(),  // text_secondary
            0xFF9AA3B2.toInt() to 0xFF7C8595.toInt(),  // text_hint
        )
        findViewById<ScrollView>(R.id.mainScroll)
            .setBackgroundColor(0xFF0F1115.toInt())
        findViewById<ScrollView>(R.id.drawerScroll)
            .setBackgroundColor(0xFF1B1F27.toInt())
        findViewById<LinearLayout>(R.id.statusCard)
            .setBackgroundResource(R.drawable.card_bg_dark)
        findViewById<Button>(R.id.btnStop)
            .setBackgroundResource(R.drawable.btn_secondary_dark)
        findViewById<Button>(R.id.btnConfig).apply {
            setBackgroundResource(R.drawable.btn_outline_dark)
            setTextColor(0xFF6AA5EC.toInt())
        }
        walk(root, textMap)
    }

    private fun walk(v: View, textMap: Map<Int, Int>) {
        if (v is EditText) {
            v.setTextColor(0xFFE8EAED.toInt())
            v.setHintTextColor(0xFF7C8595.toInt())
        } else if (v is TextView) {
            v.textColors?.defaultColor
                ?.let { c -> textMap[c]?.let { v.setTextColor(it) } }
            v.hintTextColors?.defaultColor
                ?.let { c -> textMap[c]?.let { v.setHintTextColor(it) } }
        } else if (v.javaClass == View::class.java && v.background != null) {
            // the thin divider lines inside the drawer
            v.setBackgroundColor(0xFF2A2F3A.toInt())
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), textMap)
        }
    }

    private fun indexOf(arrayRes: Int, value: String): Int {
        val arr = resources.getStringArray(arrayRes)
        val i = arr.indexOf(value)
        return if (i >= 0) i else 0
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, getString(R.string.toast_authorized), Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val openDrawer = { drawer.openDrawer(GravityCompat.START) }
        findViewById<TextView>(R.id.btnMenu).setOnClickListener { openDrawer() }
        findViewById<Button>(R.id.btnConfig).setOnClickListener { openDrawer() }
        findViewById<TextView>(R.id.btnDark).apply {
            text = if (dark) "☀" else "🌙"
            setOnClickListener { dark = !dark; recreate() }
        }
        findViewById<TextView>(R.id.btnLang).setOnClickListener {
            val cur = resources.configuration.locales[0].language
            lang = if (cur.startsWith("zh")) "en" else "zh"
            recreate()
        }
        // Samsung's gesture navigation owns the left edge — drawer opens
        // via the ☰ / bottom button only; edge swipe stays system "back".
        findViewById<Button>(R.id.btnStart).setOnClickListener { startTranslating() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            CaptionA11yService.instance?.stopTranslating()
            updateStatus()
        }
    }

    private fun setupSeekBars() {
        val sbLines = findViewById<SeekBar>(R.id.sbLines)
        val sbFont = findViewById<SeekBar>(R.id.sbFont)
        val sbAlpha = findViewById<SeekBar>(R.id.sbAlpha)

        sbLines.progress = lastLines - 1
        tvLastLines.text = getString(R.string.label_last_lines, lastLines)
        sbLines.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                lastLines = p + 1
                tvLastLines.text = getString(R.string.label_last_lines, p + 1)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        sbFont.progress = (fontSp - 12f).toInt().coerceIn(0, 16)
        sbAlpha.progress = bgAlpha
        sbFont.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                fontSp = 12f + p
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                bgAlpha = p
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun startTranslating() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.toast_need_overlay), Toast.LENGTH_LONG).show()
            return
        }
        saveApiKey()
        val missing = when (engine) {
            "baidu" -> baiduAppId.isBlank() || baiduKey.isBlank()
            "deepseek" -> deepseekKey.isBlank()
            else -> qwenKey.isBlank()
        }
        if (missing) {
            Toast.makeText(this, getString(R.string.toast_need_key), Toast.LENGTH_LONG).show()
            return
        }
        val svc = CaptionA11yService.instance
        if (svc == null) {
            Toast.makeText(
                this, getString(R.string.toast_need_a11y), Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        svc.startTranslating()
        Toast.makeText(this, getString(R.string.toast_started), Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        val running = CaptionA11yService.isActive()
        tvStatus.text = getString(
            if (running) R.string.status_running else R.string.status_stopped
        )
        tvStatus.setTextColor(
            if (running) 0xFF2E7D32.toInt() else 0xFF5A6474.toInt()
        )
        val a11y = CaptionA11yService.instance != null
        tvA11y.text = getString(if (a11y) R.string.a11y_on else R.string.a11y_off)
        tvA11y.setTextColor(
            if (a11y) 0xFF2E7D32.toInt() else 0xFF5A6474.toInt()
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
