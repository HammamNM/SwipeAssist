package com.hammam.swipeassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.FingerprintGestureController
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class SwipeAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_TOGGLE_DOCK = "com.hammam.swipeassist.ACTION_TOGGLE_DOCK"
        private const val CHANNEL_ID = "swipe_assist_channel"
        private const val NOTIF_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var dockView: View? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var isDockVisible = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isJumpingToTop = false
    private var isHoldingScroll = false
    private var holdDownDirection = true
    private var fpCallbackRegistered = false

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TOGGLE_DOCK) {
                toggleDockVisibility()
            }
        }
    }

    private val holdScrollRunnable = object : Runnable {
        override fun run() {
            if (!isHoldingScroll) return
            performHoldStep(down = holdDownDirection)
            mainHandler.postDelayed(this, 95L)
        }
    }

    private val fpGestureCallback = object : FingerprintGestureController.FingerprintGestureCallback() {
        override fun onGestureDetected(gesture: Int) {
            if (!ScrollPrefs.isFingerprintEnabled(this@SwipeAccessibilityService)) return
            when (gesture) {
                FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN -> performHoldStep(down = true)
                FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP -> performHoldStep(down = false)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerToggleReceiver()
        showControlNotification()
        setupFloatingDock()
        registerFingerprintGesturesIfSupported()
    }

    private fun registerToggleReceiver() {
        val filter = IntentFilter(ACTION_TOGGLE_DOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(toggleReceiver, filter)
        }
    }

    private fun showControlNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SwipeAssist Control",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val toggleIntent = Intent(ACTION_TOGGLE_DOCK).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (isDockVisible) "إخفاء الشريط الجانبي" else "إظهار الشريط الجانبي"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notif = builder
            .setContentTitle("SwipeAssist نشط")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        manager.notify(NOTIF_ID, notif)
    }

    private fun toggleDockVisibility() {
        isDockVisible = !isDockVisible
        stopHoldScroll()
        stopJumpToTop()
        if (isDockVisible) {
            setupFloatingDock()
            dockView?.visibility = View.VISIBLE
        } else {
            dockView?.visibility = View.GONE
        }
        showControlNotification()
    }

    private fun registerFingerprintGesturesIfSupported() {
        val controller = fingerprintGestureController ?: return
        if (controller.isGestureDetectionAvailable && !fpCallbackRegistered) {
            controller.registerFingerprintGestureCallback(fpGestureCallback, mainHandler)
            fpCallbackRegistered = true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingDock() {
        if (dockView != null) return
        if (!Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_dock, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 320
        }

        val btnUp = view.findViewById<TextView>(R.id.btnScrollUp)
        val btnDown = view.findViewById<TextView>(R.id.btnScrollDown)
        val btnJumpTop = view.findViewById<TextView>(R.id.btnJumpTop)

        btnUp.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    stopJumpToTop()
                    startHoldScroll(down = false)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopHoldScroll()
                    true
                }
                else -> false
            }
        }

        btnDown.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    stopJumpToTop()
                    startHoldScroll(down = true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopHoldScroll()
                    true
                }
                else -> false
            }
        }

        btnJumpTop.setOnClickListener {
            stopHoldScroll()
            if (isJumpingToTop) {
                stopJumpToTop()
                btnJumpTop.text = "⏫"
            } else {
                startJumpToTop(btnJumpTop)
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        stopHoldScroll()
                        params.x = (initialX + dx).coerceAtLeast(0)
                        params.y = (initialY + dy).coerceAtLeast(0)
                        windowManager?.updateViewLayout(view, params)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopHoldScroll()
                    isDragging
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        dockView = view
        dockParams = params
    }

    private fun startHoldScroll(down: Boolean) {
        holdDownDirection = down
        isHoldingScroll = true
        mainHandler.removeCallbacks(holdScrollRunnable)
        performHoldStep(down = down)
        mainHandler.postDelayed(holdScrollRunnable, 95L)
    }

    private fun stopHoldScroll() {
        isHoldingScroll = false
        mainHandler.removeCallbacks(holdScrollRunnable)
    }

    private fun performHoldStep(down: Boolean) {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val distFactor = (ScrollPrefs.getDistancePct(this) / 100f).coerceIn(0.20f, 0.95f)
        val duration = ScrollPrefs.getDurationMs(this).coerceIn(70L, 220L)

        val height = metrics.heightPixels.toFloat()
        val span = height * distFactor * 0.22f
        val midY = height * 0.5f

        val startY = if (down) midY + (span / 2f) else midY - (span / 2f)
        val endY = if (down) midY - (span / 2f) else midY + (span / 2f)

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun startJumpToTop(btnJumpTop: TextView) {
        isJumpingToTop = true
        btnJumpTop.text = "⏹"
        var steps = 0
        val maxSteps = 45

        val runnable = object : Runnable {
            override fun run() {
                if (!isJumpingToTop || steps >= maxSteps) {
                    stopJumpToTop()
                    btnJumpTop.text = "⏫"
                    return
                }
                steps++
                performHoldStep(down = false)
                mainHandler.postDelayed(this, 160L)
            }
        }
        mainHandler.post(runnable)
    }

    private fun stopJumpToTop() {
        isJumpingToTop = false
        mainHandler.removeCallbacksAndMessages(null)
        if (isHoldingScroll) {
            mainHandler.postDelayed(holdScrollRunnable, 95L)
        }
        registerFingerprintGesturesIfSupported()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopHoldScroll()
        stopJumpToTop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHoldScroll()
        stopJumpToTop()
        try {
            unregisterReceiver(toggleReceiver)
        } catch (_: Exception) {
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIF_ID)
        if (fpCallbackRegistered) {
            fingerprintGestureController?.unregisterFingerprintGestureCallback(fpGestureCallback)
            fpCallbackRegistered = false
        }
        dockView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        dockView = null
    }
}
