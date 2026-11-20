package com.hammam.swipeassist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var tvFingerprintStatus: TextView
    private lateinit var tvDistanceLabel: TextView
    private lateinit var tvDurationLabel: TextView
    private lateinit var seekDistance: SeekBar
    private lateinit var seekDuration: SeekBar
    private lateinit var switchFingerprint: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFingerprintStatus = findViewById(R.id.tvFingerprintStatus)
        tvDistanceLabel = findViewById(R.id.tvDistanceLabel)
        tvDurationLabel = findViewById(R.id.tvDurationLabel)
        seekDistance = findViewById(R.id.seekDistance)
        seekDuration = findViewById(R.id.seekDuration)
        switchFingerprint = findViewById(R.id.switchFingerprint)

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnAccessibilityPerm).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val initialDist = ScrollPrefs.getDistancePct(this)
        val initialDur = ScrollPrefs.getDurationMs(this).toInt()
        seekDistance.progress = initialDist
        seekDuration.progress = initialDur
        tvDistanceLabel.text = "مسافة السحب: $initialDist%"
        tvDurationLabel.text = "مدة السحب (كلما قلّت كان أسرع): $initialDur ms"

        seekDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val valid = progress.coerceAtLeast(20)
                tvDistanceLabel.text = "مسافة السحب: $valid%"
                ScrollPrefs.setDistancePct(this@MainActivity, valid)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val valid = progress.coerceAtLeast(60)
                tvDurationLabel.text = "مدة السحب (كلما قلّت كان أسرع): $valid ms"
                ScrollPrefs.setDurationMs(this@MainActivity, valid.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        switchFingerprint.isChecked = ScrollPrefs.isFingerprintEnabled(this)
        switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            ScrollPrefs.setFingerprintEnabled(this, isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        updateFingerprintStatusText()
    }

    private fun updateFingerprintStatusText() {
        val overlayOk = Settings.canDrawOverlays(this)
        val service = getSystemService(ACCESSIBILITY_SERVICE)
        val hasFpSystemService = service != null
        val overlayStatus = if (overlayOk) "مفعّلة" else "غير مفعّلة"
        val fpHint = if (hasFpSystemService) {
            "سيتم فحص دعم إيماءات البصمة تلقائياً عند تفعيل خدمة إمكانية الوصول (في المستشعرات الجانبية قد لا تكون مفعّلة في الهاردوير)."
        } else {
            "غير متاح"
        }
        tvFingerprintStatus.text = "صلاحية الظهور فوق التطبيقات: $overlayStatus\nملاحظة البصمة: $fpHint"
    }
}
