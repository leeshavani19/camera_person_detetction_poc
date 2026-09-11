package com.example.camera_person_detetction_poc

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import android.util.Log

import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.example.camera_person_detetction_poc.ai.AgePredictor
import com.example.camera_person_detetction_poc.ai.EmotionPredictor
import com.example.camera_person_detetction_poc.ai.GenderPredictor
import com.example.camera_person_detetction_poc.ai.ObjectDetectorManager

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel


import java.util.Locale


class MainActivity : FlutterActivity() {

    companion object {

        private const val TAG = "CameraPOC"

        private const val CAMERA_PERMISSION_REQUEST = 100

        private const val AI_CHANNEL =
            "camera_person_detetction_poc/ai"

        private const val FACE_PADDING = 30
    }

    // ============================================================
    // AI MODELS
    // ============================================================

    private lateinit var objectDetectorManager: ObjectDetectorManager

    private var genderPredictor: GenderPredictor? = null

    private var agePredictor: AgePredictor? = null


    private var emotionPredictor: EmotionPredictor? = null

    // ============================================================
    // EXECUTORS
    // ============================================================

    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val frameChannel = Channel<ByteArray>(Channel.CONFLATED)

    // ============================================================
    // STATE
    // ============================================================

    @Volatile
    private var aiReady = false

    @Volatile
    private var aiRunning = false


    @Volatile
    private var genderInferenceRunning = false

    @Volatile
    private var ageInferenceRunning = false

    @Volatile
    private var emotionInferenceRunning = false

    private lateinit var methodChannel: MethodChannel

    // ============================================================
    // ACTIVITY
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity onCreate")

        /*
         * IMPORTANT:
         *
         * DO NOT start CameraManager.
         *
         * WebRTC owns the physical camera.
         */
        checkCameraPermission()

        initializeAi()
    }

    // ============================================================
    // FLUTTER METHOD CHANNEL
    // ============================================================

    override fun configureFlutterEngine(
        @NonNull flutterEngine: FlutterEngine
    ) {
        super.configureFlutterEngine(
            flutterEngine
        )

        methodChannel =
            MethodChannel(
                flutterEngine
                    .dartExecutor
                    .binaryMessenger,
                AI_CHANNEL
            )

        methodChannel.setMethodCallHandler {
                call,
                result ->

            when (call.method) {

                // ------------------------------------------------
                // START AI
                // ------------------------------------------------

                "startAi" -> {

                    aiRunning = true

                    Log.d(
                        TAG,
                        "AI processing ENABLED"
                    )

                    result.success(
                        true
                    )
                }

                // ------------------------------------------------
                // STOP AI
                // ------------------------------------------------

                "stopAi" -> {

                    aiRunning = false

                    Log.d(
                        TAG,
                        "AI processing DISABLED"
                    )

                    result.success(
                        true
                    )
                }

                // ------------------------------------------------
                // WEBRTC FRAME
                // ------------------------------------------------

                "processCameraFrame" -> {

                    val bytes =
                        call.argument<ByteArray>(
                            "bytes"
                        )

                    if (
                        bytes == null ||
                        bytes.isEmpty()
                    ) {

                        result.error(
                            "INVALID_FRAME",
                            "Frame bytes are empty",
                            null
                        )

                        return@setMethodCallHandler
                    }

                    processWebRtcFrame(
                        bytes
                    )

                    result.success(
                        true
                    )
                }

                else -> {
                    result.notImplemented()
                }
            }
        }

        Log.d(
            TAG,
            "AI MethodChannel registered"
        )
    }

    // ============================================================
    // AI INITIALIZATION
    // ============================================================

    private fun initializeAi() {

        aiScope.launch {

            try {
                Log.d(TAG, "Initializing AI pipeline")

                objectDetectorManager = ObjectDetectorManager(this@MainActivity)

                objectDetectorManager.init(
                    onResult = {
                            detectionResult,
                            cameraTag,
                            sourceBitmap ->

                        handleDetectionResult(
                            detectionResult,
                            cameraTag,
                            sourceBitmap
                        )
                    },

                    onError = { error ->

                        Log.e(
                            TAG,
                            "Object detector error",
                            error
                        )
                    }
                )

                initializeGender()

                initializeAge()

                initializeEmotion()

                aiReady = true

                startFrameProcessor()

                Log.d(
                    TAG,
                    "AI PIPELINE READY"
                )

            } catch (e: Throwable) {

                aiReady = false

                Log.e(
                    TAG,
                    "AI initialization failed",
                    e
                )
            }
        }
    }

    private suspend fun initializeGender() {
        try {
            Log.d(TAG, "Initializing GenderPredictor")

            genderPredictor = GenderPredictor(this@MainActivity)

            Log.d(TAG, "GenderPredictor READY")

        } catch (e: Throwable) {
            genderPredictor = null

            Log.e(
                TAG,
                "GenderPredictor initialization failed",
                e
            )
        }
    }


    private suspend fun initializeAge() {

        try {

            Log.d(TAG, "Initializing AgePredictor")

            agePredictor = AgePredictor(this@MainActivity)

            Log.d(TAG, "AgePredictor READY")

        } catch (e: Throwable) {

            agePredictor = null

            Log.e(TAG, "AgePredictor initialization failed", e)
        }
    }

    private suspend fun initializeEmotion() {

        try {

            Log.d(
                TAG,
                "Initializing EmotionPredictor"
            )

            emotionPredictor =
                EmotionPredictor(
                    this@MainActivity
                )

            Log.d(
                TAG,
                "EmotionPredictor READY"
            )

        } catch (e: Throwable) {

            emotionPredictor = null

            Log.e(
                TAG,
                "EmotionPredictor initialization failed",
                e
            )
        }
    }

    // ============================================================
    // RECEIVE FRAME FROM WEBRTC
    // ============================================================

    private fun processWebRtcFrame(
        bytes: ByteArray
    ) {

        if (!aiReady) {
            return
        }

        if (!aiRunning) {
            return
        }

        frameChannel.trySend(bytes)
    }

    private fun startFrameProcessor() {

        aiScope.launch {

            for (bytes in frameChannel) {

                if (!aiReady || !aiRunning) {
                    continue
                }

                try {

                    Log.d(TAG, "Processing latest WebRTC frame: ${bytes.size} bytes")

                    val bitmap = BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.size
                        )

                    if (bitmap == null) {

                        Log.e(TAG, "Unable to decode WebRTC frame")
                        continue
                    }

                    objectDetectorManager.processBitmap(
                        bitmap,
                        "webrtc"
                    )

                } catch (e: Throwable) {

                    Log.e(
                        TAG,
                        "WebRTC AI frame processing failed",
                        e
                    )
                }
            }
        }
    }

    // ============================================================
    // OBJECT DETECTION RESULT
    // ============================================================

    private fun handleDetectionResult(
        result:
        com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult,

        cameraTag: String,

        sourceBitmap: Bitmap
    ) {

        val detections =
            result.detections()

        Log.i(
            TAG,
            "Object Detection → "
                    + "${detections.size} objects"
                    + " | camera=$cameraTag"
        )

        // ========================================================
        // FIND PERSON
        // ========================================================

        val person =
            detections.firstOrNull { detection ->

                detection
                    .categories()
                    .firstOrNull()
                    ?.categoryName()
                    ?.equals(
                        "person",
                        ignoreCase = true
                    ) == true
            }

        if (person == null) {

            Log.d(
                TAG,
                "No person detected"
            )

            return
        }

        val box =
            person.boundingBox()

        Log.d(
            TAG,
            "PERSON DETECTED → $box"
        )

        // ========================================================
        // PERSON BOUNDING BOX
        // ========================================================

        val left =
            box.left
                .toInt()
                .coerceIn(
                    0,
                    sourceBitmap.width
                )

        val top =
            box.top
                .toInt()
                .coerceIn(
                    0,
                    sourceBitmap.height
                )

        val right =
            box.right
                .toInt()
                .coerceIn(
                    0,
                    sourceBitmap.width
                )

        val bottom =
            box.bottom
                .toInt()
                .coerceIn(
                    0,
                    sourceBitmap.height
                )

        if (
            right <= left ||
            bottom <= top
        ) {

            Log.d(
                TAG,
                "Invalid person bounding box"
            )

            return
        }

        // ========================================================
        // 30PX PADDING
        // ========================================================

        val paddedRect =
            Rect(
                (
                        left -
                                FACE_PADDING
                        ).coerceAtLeast(
                        0
                    ),

                (
                        top -
                                FACE_PADDING
                        ).coerceAtLeast(
                        0
                    ),

                (
                        right +
                                FACE_PADDING
                        ).coerceAtMost(
                        sourceBitmap.width
                    ),

                (
                        bottom +
                                FACE_PADDING
                        ).coerceAtMost(
                        sourceBitmap.height
                    )
            )

        val cropWidth =
            paddedRect.width()

        val cropHeight =
            paddedRect.height()

        if (
            cropWidth <= 0 ||
            cropHeight <= 0
        ) {

            return
        }

        val faceCrop =
            try {

                Bitmap.createBitmap(
                    sourceBitmap,
                    paddedRect.left,
                    paddedRect.top,
                    cropWidth,
                    cropHeight
                )

            } catch (e: Throwable) {

                Log.e(
                    TAG,
                    "Crop creation failed",
                    e
                )

                return
            }

        Log.d(
            TAG,
            "MODEL CROP → "
                    + "left=${paddedRect.left} "
                    + "top=${paddedRect.top} "
                    + "right=${paddedRect.right} "
                    + "bottom=${paddedRect.bottom} "
                    + "width=${faceCrop.width} "
                    + "height=${faceCrop.height}"
        )

        // Each model is launched as an independent coroutine.
        // All inference runs on aiScope's Dispatchers.Default.

        runGender(
            faceCrop
        )

        runAge(
            faceCrop
        )

        runEmotion(
            faceCrop
        )
    }

    // ============================================================
    // GENDER
    // ============================================================

    private fun runGender(faceCrop: Bitmap) {

        val predictor = genderPredictor ?: run {
            Log.d(TAG, "GenderPredictor not ready")
            return
        }

        if (genderInferenceRunning) {
            return
        }

        genderInferenceRunning = true

        aiScope.launch {

            val startTime = System.currentTimeMillis()

            try {

                Log.d(TAG, "Starting gender inference")

                val result = predictor.predict(faceCrop)

                val duration =
                    System.currentTimeMillis() - startTime

                Log.i(
                    TAG,
                    "GENDER RESULT → " +
                            "${result.gender} " +
                            "confidence=" +
                            String.format(
                                Locale.US,
                                "%.2f",
                                result.confidence
                            ) +
                            " | inference=${duration}ms"
                )

            } catch (e: Throwable) {

                Log.e(
                    TAG,
                    "Gender inference failed",
                    e
                )

            } finally {

                genderInferenceRunning = false
            }
        }
    }

    // ============================================================
    // AGE
    // ============================================================

    private fun runAge(faceCrop: Bitmap) {

        val predictor = agePredictor ?: run {
            Log.d(TAG, "AgePredictor not ready")
            return
        }

        if (ageInferenceRunning) {
            return
        }

        ageInferenceRunning = true

        aiScope.launch {

            val startTime = System.currentTimeMillis()

            try {

                Log.d(TAG, "Starting age inference")

                val result = predictor.predict(faceCrop)

                val duration =
                    System.currentTimeMillis() - startTime

                Log.i(
                    TAG,
                    "AGE RESULT → " +
                            "${result.ageClass} " +
                            "confidence=" +
                            String.format(
                                Locale.US,
                                "%.2f",
                                result.confidence
                            ) +
                            " age=" +
                            result.age +
                            " | inference=${duration}ms"
                )

            } catch (e: Throwable) {

                Log.e(
                    TAG,
                    "Age inference failed",
                    e
                )

            } finally {

                ageInferenceRunning = false
            }
        }
    }

    // ============================================================
    // EMOTION
    // ============================================================

    private fun runEmotion(faceCrop: Bitmap) {

        val predictor = emotionPredictor ?: run {
            Log.d(TAG, "EmotionPredictor not ready")
            return
        }

        if (emotionInferenceRunning) {
            return
        }

        emotionInferenceRunning = true

        aiScope.launch {

            val startTime = System.currentTimeMillis()

            try {

                Log.d(TAG, "Starting emotion inference")

                val result = predictor.predict(faceCrop)

                val duration =
                    System.currentTimeMillis() - startTime

                if (result != null) {

                    Log.i(
                        TAG,
                        "EMOTION RESULT → " +
                                "$result" +
                                " | inference=${duration}ms"
                    )
                }

            } catch (e: Throwable) {

                Log.e(
                    TAG,
                    "Emotion inference failed",
                    e
                )

            } finally {

                emotionInferenceRunning = false
            }
        }
    }

    // ============================================================
    // CAMERA PERMISSION
    //
    // Permission only.
    //
    // This does NOT start the camera.
    // WebRTC still owns the camera.
    // ============================================================

    private fun checkCameraPermission() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "Camera permission already granted")

        } else {

            Log.d(TAG, "Requesting camera permission")

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    // ============================================================
    // PERMISSION RESULT
    // ============================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            CAMERA_PERMISSION_REQUEST
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                Log.d(
                    TAG,
                    "Camera permission granted"
                )

            } else {

                Log.e(
                    TAG,
                    "Camera permission denied"
                )
            }
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        Log.d(TAG, "MainActivity onDestroy")

        aiRunning = false
        aiReady = false

        // Close predictors
        try {
            genderPredictor?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "GenderPredictor close failed", e)
        }

        genderPredictor = null

        try {
            agePredictor?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "AgePredictor close failed", e)
        }

        agePredictor = null

        try {
            emotionPredictor?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "EmotionPredictor close failed", e)
        }

        emotionPredictor = null

        // Close object detector
        try {
            if (::objectDetectorManager.isInitialized) {
                objectDetectorManager.close()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "ObjectDetectorManager close failed", e)
        }

        // Cancel all coroutines
        aiScope.cancel()

        super.onDestroy()
    }
}