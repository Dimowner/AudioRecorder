package com.dimowner.audiorecorder.v2.app.overlay

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.v2.app.HomeActivity
import com.dimowner.audiorecorder.v2.audio.AudioRecordingService
import com.dimowner.audiorecorder.v2.audio.AudioRecordingServiceEvent
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.model.Record
import com.dimowner.audiorecorder.v2.data.model.RenameSpeechMode
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@AndroidEntryPoint
class FloatingRecorderOverlayService : Service() {

    @Inject lateinit var prefs: PrefsV2
    @Inject lateinit var recordsDataSource: RecordsDataSource
    @Inject lateinit var audioPlayer: PlayerContractNew.Player
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }
    private var recordingStateJob: Job? = null
    private var recordingEventJob: Job? = null

    private lateinit var windowManager: WindowManager
    private var iconView: FrameLayout? = null
    private var iconParams: WindowManager.LayoutParams? = null
    private var renameView: View? = null
    private var renameSpeechRecognizer: SpeechRecognizer? = null
    private var renameSpeechListening = false
    private var saveFeedbackAnimator: AnimatorSet? = null
    private var recordingService: AudioRecordingService? = null
    private var isRecordingServiceBound = false
    private var pendingStop = false
    private var isRecording = false

    private val recordingServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioRecordingService.ServiceBinder
            recordingService = binder?.getService()
            isRecordingServiceBound = recordingService != null
            recordingService?.let { boundService ->
                subscribeRecordingService(boundService)
                if (pendingStop) {
                    pendingStop = false
                    boundService.stopRecording()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isRecordingServiceBound = false
            recordingStateJob?.cancel()
            recordingEventJob?.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundServiceNotification()
        bindRecordingService()

        if (!prefs.isFloatingRecorderOverlayEnabled || !FloatingRecorderOverlayPermission.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        addIconOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.isFloatingRecorderOverlayEnabled || !FloatingRecorderOverlayPermission.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (iconView == null) {
            addIconOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeRenameOverlay()
        removeIconOverlay()
        unbindRecordingService()
        recordingStateJob?.cancel()
        recordingEventJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun subscribeRecordingService(service: AudioRecordingService) {
        recordingStateJob?.cancel()
        recordingStateJob = serviceScope.launch {
            service.recordingState.collect { state ->
                val nowRecording = state.isRecording()
                if (isRecording != nowRecording) {
                    isRecording = nowRecording
                    iconView?.post { updateIconAppearance(nowRecording) }
                }
            }
        }

        recordingEventJob?.cancel()
        recordingEventJob = serviceScope.launch {
            service.event.collect { event ->
                when (event) {
                    is AudioRecordingServiceEvent.RecordingStopped -> handleRecordingStopped(event.recordId)
                    is AudioRecordingServiceEvent.ShowErrorSnack -> {
                        Timber.w("Floating recorder start/stop error: ${event.message}")
                        iconView?.post { updateIconAppearance(false) }
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun handleRecordingStopped(recordId: Long) {
        iconView?.post { runSavedAnimation() }
        if (prefs.askToRenameAfterRecordingStopped && recordId >= 0) {
            recordsDataSource.getRecord(recordId)?.let { record ->
                iconView?.post { showRenameOverlay(record) }
            }
        }
    }

    private fun addIconOverlay() {
        if (iconView != null) return

        val defaultSize = dp(DEFAULT_ICON_SIZE_DP)
        val displayMetrics = resources.displayMetrics
        val size = clampOverlaySize(
            savedSize = prefs.floatingRecorderOverlaySize,
            defaultSize = defaultSize,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
        )
        val position = clampOverlayPosition(
            savedX = prefs.floatingRecorderOverlayX,
            savedY = prefs.floatingRecorderOverlayY,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            overlayWidth = size,
            overlayHeight = size,
        )

        val view = FrameLayout(this).apply {
            background = iconBubbleDrawable(IDLE_ICON_COLOR)
            elevation = dp(8).toFloat()
            addView(View(this@FloatingRecorderOverlayService).apply {
                background = recordDiscDrawable()
            }, FrameLayout.LayoutParams(recordDiscSize(size), recordDiscSize(size), Gravity.CENTER))
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }

        view.setOnTouchListener(OverlayTouchListener(params))
        iconView = view
        iconParams = params
        windowManager.addView(view, params)
        updateIconAppearance(false)
    }

    private fun removeIconOverlay() {
        iconView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        iconView = null
        iconParams = null
    }

    private inner class OverlayTouchListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@FloatingRecorderOverlayService).scaledTouchSlop
        private val longPressTimeout = ViewConfiguration.getLongPressTimeout()
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var downTime = 0L
        private var dragging = false
        private var pinching = false
        private var suppressTap = false
        private var initialPinchDistance = 0f
        private var initialPinchSize = 0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    downTime = event.eventTime
                    dragging = false
                    pinching = false
                    suppressTap = false
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        beginPinch(event)
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pinching || event.pointerCount >= 2) {
                        if (!pinching) beginPinch(event)
                        updatePinch(view, event)
                        return true
                    }

                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    val movedEnough = abs(deltaX) > touchSlop || abs(deltaY) > touchSlop
                    val heldLongEnough = event.eventTime - downTime >= longPressTimeout
                    if (movedEnough && heldLongEnough) {
                        dragging = true
                        params.x = startX + deltaX.toInt()
                        params.y = startY + deltaY.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (pinching) finishPinch(view)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (pinching) {
                        finishPinch(view)
                    } else if (dragging) {
                        persistIconPosition(params)
                    } else if (!suppressTap) {
                        handleIconTap()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (pinching) {
                        finishPinch(view)
                    } else if (dragging) {
                        persistIconPosition(params)
                    }
                    return true
                }
            }
            return false
        }

        private fun beginPinch(event: MotionEvent) {
            val distance = pointerDistance(event)
            if (distance <= 0f) return

            // Once a second finger joins, the gesture is resize-only. This prevents an
            // accidental start/stop tap or long-press drag after the pinch ends.
            pinching = true
            suppressTap = true
            dragging = false
            initialPinchDistance = distance
            initialPinchSize = params.width.takeIf { it > 0 } ?: dp(DEFAULT_ICON_SIZE_DP)
        }

        private fun updatePinch(view: View, event: MotionEvent) {
            if (event.pointerCount < 2 || initialPinchDistance <= 0f) return

            val scale = pointerDistance(event) / initialPinchDistance
            val metrics = resources.displayMetrics
            val size = clampOverlaySize(
                savedSize = (initialPinchSize * scale).roundToInt(),
                defaultSize = dp(DEFAULT_ICON_SIZE_DP),
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
            )
            params.width = size
            params.height = size
            updateRecordDiscLayout(view as FrameLayout, size)

            val clamped = clampOverlayPosition(
                savedX = params.x,
                savedY = params.y,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                overlayWidth = size,
                overlayHeight = size,
            )
            params.x = clamped.x
            params.y = clamped.y
            windowManager.updateViewLayout(view, params)
        }

        private fun finishPinch(view: View) {
            if (!pinching) return

            pinching = false
            persistIconPosition(params)
            updateRecordDiscLayout(view as FrameLayout, params.width.takeIf { it > 0 } ?: dp(DEFAULT_ICON_SIZE_DP))
        }

        private fun pointerDistance(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f

            val deltaX = event.getX(0) - event.getX(1)
            val deltaY = event.getY(0) - event.getY(1)
            return sqrt(deltaX * deltaX + deltaY * deltaY)
        }
    }

    private fun persistIconPosition(params: WindowManager.LayoutParams) {
        val size = params.width.takeIf { it > 0 } ?: dp(DEFAULT_ICON_SIZE_DP)
        val metrics = resources.displayMetrics
        val clamped = clampOverlayPosition(
            savedX = params.x,
            savedY = params.y,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            overlayWidth = size,
            overlayHeight = size,
        )
        params.x = clamped.x
        params.y = clamped.y
        iconView?.let { windowManager.updateViewLayout(it, params) }
        prefs.floatingRecorderOverlayX = clamped.x
        prefs.floatingRecorderOverlayY = clamped.y
        prefs.floatingRecorderOverlaySize = size
    }

    private fun updateRecordDiscLayout(view: FrameLayout, overlaySize: Int) {
        val disc = view.getChildAt(0) ?: return
        val size = recordDiscSize(overlaySize)
        val currentParams = disc.layoutParams as? FrameLayout.LayoutParams
        disc.layoutParams = (currentParams ?: FrameLayout.LayoutParams(size, size, Gravity.CENTER)).apply {
            width = size
            height = size
            gravity = Gravity.CENTER
        }
    }

    private fun recordDiscSize(overlaySize: Int): Int {
        return calculateRecordDiscSize(
            overlaySize = overlaySize,
            defaultOverlaySize = dp(DEFAULT_ICON_SIZE_DP),
            defaultDiscSize = dp(DEFAULT_RECORD_DISC_SIZE_DP),
        )
    }

    private fun handleIconTap() {
        if (isRecording) {
            recordingService?.stopRecording() ?: run {
                pendingStop = true
                bindRecordingService()
            }
        } else {
            audioPlayer.stop()
            AudioRecordingService.startServiceForeground(
                context = applicationContext,
                startedFromFloatingOverlay = true,
            )
        }
    }

    private fun updateIconAppearance(recording: Boolean) {
        if (recording) {
            saveFeedbackAnimator?.cancel()
            saveFeedbackAnimator = null
            updateIconBackground(RECORDING_ICON_COLOR)
        } else if (saveFeedbackAnimator?.isRunning != true) {
            updateIconBackground(IDLE_ICON_COLOR)
        }
    }

    private fun updateIconBackground(color: Int) {
        iconView?.background = iconBubbleDrawable(color)
    }

    private fun runSavedAnimation() {
        val view = iconView ?: return
        saveFeedbackAnimator?.cancel()

        val scaleUpX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.18f)
        val scaleUpY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.18f)
        val scaleDownX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.18f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.18f, 1f)
        val scalePulse = AnimatorSet().apply {
            play(scaleUpX).with(scaleUpY)
            play(scaleDownX).with(scaleDownY).after(scaleUpX)
            duration = SCALE_FEEDBACK_DURATION_MS
        }

        val colorFeedback = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SAVE_FEEDBACK_DURATION_MS
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                updateIconBackground(calculateSaveFeedbackColor(progress, IDLE_ICON_COLOR))
            }
        }

        saveFeedbackAnimator = AnimatorSet().apply {
            playTogether(scalePulse, colorFeedback)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) {
                        updateIconBackground(IDLE_ICON_COLOR)
                    }
                    if (saveFeedbackAnimator === animation) {
                        saveFeedbackAnimator = null
                    }
                }
            })
            start()
        }
    }

    private fun showRenameOverlay(record: Record) {
        removeRenameOverlay()

        val metrics = resources.displayMetrics
        val panelWidth = calculateRenamePanelWidth(metrics.widthPixels)
        val position = clampOverlayPosition(
            savedX = prefs.floatingRecorderRenameOverlayX,
            savedY = prefs.floatingRecorderRenameOverlayY,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            overlayWidth = panelWidth,
            // The panel height is content-driven, so use a conservative first-pass estimate.
            // A measured-height clamp runs immediately after attachment and on every drag end.
            overlayHeight = dp(RENAME_PANEL_ESTIMATED_HEIGHT_DP),
        )
        val style = renameOverlayStyle(isDarkTheme = prefs.isDarkTheme)

        val input = EditText(this).apply {
            setText(record.name)
            selectAll()
            setSingleLine(true)
            setTextColor(style.textColor)
            typeface = Typeface.DEFAULT_BOLD
            backgroundTintList = ColorStateList.valueOf(style.textColor)
        }
        val error = TextView(this).apply {
            setTextColor(RECORDING_ICON_COLOR)
            visibility = View.GONE
        }
        val speechButton = createRenameSpeechButton(input, error)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedDrawable(style.panelColor, dp(16).toFloat())
            addView(TextView(this@FloatingRecorderOverlayService).apply {
                text = getString(R.string.update_record_name)
                setTextColor(style.textColor)
                textSize = 18f
                setPadding(0, 0, 0, dp(8))
            })
            addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(error, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(speechButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(6)
            })
            addView(LinearLayout(this@FloatingRecorderOverlayService).apply {
                gravity = Gravity.END
                addView(Button(this@FloatingRecorderOverlayService).apply {
                    text = getString(R.string.keep_default_name)
                    setOnClickListener { removeRenameOverlay() }
                })
                addView(Button(this@FloatingRecorderOverlayService).apply {
                    text = getString(R.string.btn_save)
                    setOnClickListener { saveRename(record, input.text.toString(), error) }
                })
            })
        }

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // Only the non-editable header starts a drag; the text field and buttons keep their
        // normal focus/click behavior, which is important because this overlay owns keyboard input.
        (panel.getChildAt(0) as View).setOnTouchListener(RenameOverlayTouchListener(params))

        renameView = panel
        windowManager.addView(panel, params)
        panel.post { clampAndPersistRenamePosition(panel, params, persist = false) }
        input.requestFocus()
        input.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun createRenameSpeechButton(input: EditText, error: TextView): LinearLayout {
        val modeLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            text = renameSpeechModeText(prefs.floatingRecorderRenameSpeechMode)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(RENAME_SPEECH_BUTTON_MIN_HEIGHT_DP)
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = roundedDrawable(IDLE_ICON_COLOR, dp(18).toFloat())
            addView(ImageView(this@FloatingRecorderOverlayService).apply {
                setImageResource(R.drawable.ic_mic)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                contentDescription = getString(R.string.rename_speech_listening)
            }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(modeLabel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            setOnClickListener {
                startRenameSpeechRecognition(input = input, error = error, micButton = this, modeLabel = modeLabel)
            }
            setOnLongClickListener {
                showRenameSpeechModePopup(anchor = this, modeLabel = modeLabel)
                true
            }
        }
    }

    private fun showRenameSpeechModePopup(anchor: View, modeLabel: TextView) {
        PopupMenu(this, anchor).apply {
            menu.add(0, RenameSpeechMode.Append.persistedValue, 0, getString(R.string.rename_speech_mode_append))
            menu.add(0, RenameSpeechMode.Replace.persistedValue, 1, getString(R.string.rename_speech_mode_replace))
            setOnMenuItemClickListener { item ->
                val mode = RenameSpeechMode.fromPersistedValue(item.itemId)
                prefs.floatingRecorderRenameSpeechMode = mode
                modeLabel.text = renameSpeechModeText(mode)
                true
            }
            show()
        }
    }

    private fun startRenameSpeechRecognition(
        input: EditText,
        error: TextView,
        micButton: View,
        modeLabel: TextView,
    ) {
        if (renameSpeechListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showRenameInlineMessage(error, R.string.rename_speech_no_recognizer)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showRenameInlineMessage(error, R.string.rename_speech_no_microphone_permission)
            return
        }

        destroyRenameSpeechRecognizer()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        renameSpeechRecognizer = recognizer
        renameSpeechListening = true
        micButton.isEnabled = false
        showRenameInlineMessage(error, R.string.rename_speech_listening)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(errorCode: Int) {
                val message = if (errorCode == SpeechRecognizer.ERROR_NO_MATCH || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    R.string.rename_speech_no_match
                } else {
                    R.string.rename_speech_error
                }
                showRenameInlineMessage(error, message)
                finishRenameSpeechRecognition(micButton)
            }

            override fun onResults(results: Bundle?) {
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                if (transcript == null) {
                    showRenameInlineMessage(error, R.string.rename_speech_no_match)
                } else {
                    val updated = applyRenameSpeechTranscription(
                        currentName = input.text.toString(),
                        transcript = transcript,
                        mode = prefs.floatingRecorderRenameSpeechMode,
                    )
                    input.setText(updated)
                    input.setSelection(updated.length)
                    error.visibility = View.GONE
                }
                finishRenameSpeechRecognition(micButton)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        runCatching { recognizer.startListening(intent) }
            .onFailure {
                Timber.e(it, "Failed to start floating rename speech recognition")
                showRenameInlineMessage(error, R.string.rename_speech_error)
                finishRenameSpeechRecognition(micButton)
            }
    }

    private fun finishRenameSpeechRecognition(micButton: View) {
        renameSpeechListening = false
        micButton.isEnabled = true
        destroyRenameSpeechRecognizer()
    }

    private fun destroyRenameSpeechRecognizer() {
        renameSpeechRecognizer?.let { recognizer ->
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
        renameSpeechRecognizer = null
        renameSpeechListening = false
    }

    private fun showRenameInlineMessage(error: TextView, messageRes: Int) {
        error.text = getString(messageRes)
        error.visibility = View.VISIBLE
    }

    private fun renameSpeechModeText(mode: RenameSpeechMode): String {
        return when (mode) {
            RenameSpeechMode.Append -> getString(R.string.rename_speech_mode_append)
            RenameSpeechMode.Replace -> getString(R.string.rename_speech_mode_replace)
        }
    }

    private inner class RenameOverlayTouchListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@FloatingRecorderOverlayService).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    val movedEnough = abs(deltaX) > touchSlop || abs(deltaY) > touchSlop
                    if (movedEnough) {
                        dragging = true
                        params.x = startX + deltaX.toInt()
                        params.y = startY + deltaY.toInt()
                        clampAndPersistRenamePosition(view.rootView, params, persist = false)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        clampAndPersistRenamePosition(view.rootView, params, persist = true)
                    } else {
                        view.performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        clampAndPersistRenamePosition(view.rootView, params, persist = true)
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun clampAndPersistRenamePosition(
        view: View,
        params: WindowManager.LayoutParams,
        persist: Boolean,
    ) {
        val metrics = resources.displayMetrics
        val clamped = clampOverlayPosition(
            savedX = params.x,
            savedY = params.y,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            overlayWidth = params.width.takeIf { it > 0 } ?: view.width,
            overlayHeight = view.height.takeIf { it > 0 } ?: dp(RENAME_PANEL_ESTIMATED_HEIGHT_DP),
        )
        params.x = clamped.x
        params.y = clamped.y
        runCatching { windowManager.updateViewLayout(view, params) }
        if (persist) {
            prefs.floatingRecorderRenameOverlayX = clamped.x
            prefs.floatingRecorderRenameOverlayY = clamped.y
        }
    }

    private fun calculateRenamePanelWidth(screenWidthPx: Int): Int {
        return calculateBoundedOverlayWidth(
            screenWidth = screenWidthPx,
            horizontalMargin = dp(16) * 2,
            minimumWidth = dp(RENAME_PANEL_MIN_WIDTH_DP),
            maximumWidth = dp(RENAME_PANEL_MAX_WIDTH_DP),
        )
    }

    private fun saveRename(record: Record, newName: String, error: TextView) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            error.text = getString(R.string.msg_name_cannot_be_empty)
            error.visibility = View.VISIBLE
            return
        }

        serviceScope.launch {
            val success = if (trimmed == record.name) {
                true
            } else {
                recordsDataSource.renameRecord(record, trimmed)
            }
            withContext(ioDispatcher) {
                iconView?.post {
                    if (success) {
                        removeRenameOverlay()
                    } else {
                        error.text = getString(R.string.msg_file_operation_failed)
                        error.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun removeRenameOverlay() {
        destroyRenameSpeechRecognizer()
        renameView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        renameView = null
    }

    private fun bindRecordingService() {
        if (!isRecordingServiceBound) {
            bindService(
                Intent(this, AudioRecordingService::class.java),
                recordingServiceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }
    }

    private fun unbindRecordingService() {
        if (isRecordingServiceBound) {
            unbindService(recordingServiceConnection)
        }
        isRecordingServiceBound = false
        recordingService = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_floating_recorder_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_floating_recorder_description)
                setShowBadge(false)
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) },
            flags,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_recorder_notification_title))
            .setContentText(getString(R.string.floating_recorder_notification_text))
            .setSmallIcon(R.drawable.ic_record_rec)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .build()
    }

    private fun iconBubbleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun recordDiscDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(RECORDING_ICON_COLOR)
            // Only the central disc has a white contour; the outer bubble stays pure state color.
            setStroke(dp(4), Color.WHITE)
        }
    }

    private fun roundedDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "floating_recorder_overlay_channel"
        private const val NOTIFICATION_ID = 1004
        private const val ACTION_STOP = "com.dimowner.audiorecorder.ACTION_STOP_FLOATING_RECORDER_OVERLAY"
        private const val SCALE_FEEDBACK_DURATION_MS = 160L
        private const val SAVE_FEEDBACK_DURATION_MS = 3000L
        private const val DEFAULT_ICON_SIZE_DP = 56
        private const val DEFAULT_RECORD_DISC_SIZE_DP = 30
        private const val RENAME_PANEL_ESTIMATED_HEIGHT_DP = 220
        private const val RENAME_PANEL_MIN_WIDTH_DP = 240
        private const val RENAME_PANEL_MAX_WIDTH_DP = 360
        private const val RENAME_SPEECH_BUTTON_MIN_HEIGHT_DP = 72
        private val IDLE_ICON_COLOR = Color.DKGRAY
        private val RECORDING_ICON_COLOR = Color.RED

        fun startService(context: Context) {
            val intent = Intent(context, FloatingRecorderOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, FloatingRecorderOverlayService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
