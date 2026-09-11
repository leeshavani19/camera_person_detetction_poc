package com.example.camera_person_detetction_poc.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.Log

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker


import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier

import org.tensorflow.lite.Interpreter

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

import kotlin.math.sqrt

class EmotionPredictor(
    private val context: Context,
    private val emotionModelAsset: String =
        "models/emotion_model.tflite",
    private val handModelAsset: String =
        "models/hand_landmarker.task",
    private val cascadeAsset: String =
        "models/haarcascade_frontalface_default.xml"
) {

    companion object {

        private const val TAG =
            "EmotionPredictor"

        private const val INPUT_WIDTH = 48
        private const val INPUT_HEIGHT = 48

        /*
         * Original FER class order:
         *
         * 0 -> Angry
         * 1 -> Disgusted
         * 2 -> Fearful
         * 3 -> Happy
         * 4 -> Neutral
         * 5 -> Sad
         * 6 -> Surprised
         */

        private const val HAPPY_INDEX = 3
        private const val NEUTRAL_INDEX = 4
        private const val SURPRISE_INDEX = 6

        /*
         * Hand landmarks used by Python implementation.
         *
         * 0  -> wrist
         * 4  -> thumb
         * 8  -> index
         * 12 -> middle
         * 16 -> ring
         * 20 -> pinky
         */
        private val HAND_KEY_POINTS = intArrayOf(
            0,
            4,
            8,
            12,
            16,
            20
        )

        /*
         * Hand is considered near chin when:
         *
         * distance < faceWidth * 0.6
         */
        private const val HAND_CHIN_DISTANCE_RATIO = 0.6
    }

    // ============================================================
    // TFLITE
    // ============================================================

    private val interpreter: Interpreter

    // ============================================================
    // HAAR CASCADE
    // ============================================================

    private val faceCascade: CascadeClassifier?

    // ============================================================
    // HAND LANDMARKER
    // ============================================================

    private val handLandmarker: HandLandmarker?

    private var handTimestampMs = 0L

    // ============================================================
    // INITIALIZATION
    // ============================================================

    init {

        // --------------------------------------------------------
        // OpenCV
        // --------------------------------------------------------

        if (!OpenCVLoader.initLocal()) {
            throw IllegalStateException(
                "OpenCV initialization failed"
            )
        }

        Log.d(
            TAG,
            "OpenCV READY"
        )

        // --------------------------------------------------------
        // TFLITE MODEL
        // --------------------------------------------------------

        interpreter =
            Interpreter(
                loadModelFile(
                    context,
                    emotionModelAsset
                ),
                Interpreter.Options().apply {
                    setNumThreads(2)
                }
            )

        val inputTensor =
            interpreter.getInputTensor(0)

        val outputTensor =
            interpreter.getOutputTensor(0)

        Log.d(
            TAG,
            "Emotion input shape = " +
                    inputTensor
                        .shape()
                        .contentToString()
        )

        Log.d(
            TAG,
            "Emotion input type = " +
                    inputTensor.dataType()
        )

        Log.d(
            TAG,
            "Emotion output shape = " +
                    outputTensor
                        .shape()
                        .contentToString()
        )

        Log.d(
            TAG,
            "Emotion output type = " +
                    outputTensor.dataType()
        )

        if (
            inputTensor.dataType() !=
            org.tensorflow.lite.DataType.FLOAT32
        ) {
            throw IllegalArgumentException(
                "Emotion model input must be FLOAT32"
            )
        }

        // --------------------------------------------------------
        // HAAR CASCADE
        // --------------------------------------------------------

        faceCascade =
            loadCascade(
                context,
                cascadeAsset
            )

        if (faceCascade == null) {

            Log.e(
                TAG,
                "Haar cascade unavailable"
            )

        } else {

            Log.d(
                TAG,
                "Haar cascade READY"
            )
        }

        // --------------------------------------------------------
        // HAND LANDMARKER
        // --------------------------------------------------------

        handLandmarker =
            try {

                val baseOptions =
                    BaseOptions
                        .builder()
                        .setModelAssetPath(
                            handModelAsset
                        )
                        .build()

                val options =
                    HandLandmarker
                        .HandLandmarkerOptions
                        .builder()
                        .setBaseOptions(
                            baseOptions
                        )
                        .setRunningMode(
                            RunningMode.VIDEO
                        )
                        .setNumHands(2)
                        .setMinHandDetectionConfidence(
                            0.5f
                        )
                        .setMinHandPresenceConfidence(
                            0.5f
                        )
                        .setMinTrackingConfidence(
                            0.5f
                        )
                        .build()

                HandLandmarker
                    .createFromOptions(
                        context,
                        options
                    )

            } catch (e: Throwable) {

                Log.e(
                    TAG,
                    "HandLandmarker initialization failed",
                    e
                )

                null
            }

        Log.i(
            TAG,
            "EmotionPredictor READY"
        )
    }

    // ============================================================
    // PUBLIC PREDICT
    // ============================================================

    @Synchronized
    fun predict(
        faceCrop: Bitmap
    ): String? {

        var gray: Mat? = null
        var grayEq: Mat? = null
        var roi: Mat? = null
        var resized: Mat? = null

        val totalStart = SystemClock.elapsedRealtime()

        try {

            Log.d(
                TAG,
                "Running emotion inference"
            )

            // ----------------------------------------------------
            // 1. Bitmap -> grayscale
            // ----------------------------------------------------

            val grayStart =
                SystemClock.elapsedRealtime()
            gray =
                bitmapToGrayMat(
                    faceCrop
                )

            val grayTime =
                SystemClock.elapsedRealtime() - grayStart

            Log.d(TAG, "Emotion bitmapToGray = ${grayTime}ms")

            // ----------------------------------------------------
            // 2. Histogram equalization
            // ----------------------------------------------------
            val equalizeStart =
                SystemClock.elapsedRealtime()

            grayEq = Mat()

            Imgproc.equalizeHist(
                gray,
                grayEq
            )

            val equalizeTime =
                SystemClock.elapsedRealtime() - equalizeStart

            Log.d(TAG, "Emotion equalizeHist = ${equalizeTime}ms")

            // ----------------------------------------------------
            // 3. Haar face detection
            // ----------------------------------------------------
            val haarStart =
                SystemClock.elapsedRealtime()
            val faceRect =
                findLargestFace(
                    grayEq
                )

            val haarTime =
                SystemClock.elapsedRealtime() - haarStart

            Log.d(TAG, "Emotion Haar = ${haarTime}ms")

            if (faceRect != null) {

                Log.d(
                    TAG,
                    "Haar face detected → " +
                            "x=${faceRect.x} " +
                            "y=${faceRect.y} " +
                            "w=${faceRect.width} " +
                            "h=${faceRect.height}"
                )

                /*
                 * Use the equalized grayscale image for
                 * the detected face ROI.
                 */
                roi =
                    Mat(
                        grayEq,
                        faceRect
                    )

            } else {

                Log.d(
                    TAG,
                    "No Haar face detected → using full crop"
                )

                /*
                 * Same fallback concept as Python:
                 * use the full supplied crop.
                 */
                roi =
                    grayEq.clone()
            }

            // ----------------------------------------------------
            // 4. Resize to 48x48
            // ----------------------------------------------------
            val roiStart = SystemClock.elapsedRealtime()

            resized = Mat()

            Imgproc.resize(
                roi,
                resized,
                Size(
                    INPUT_WIDTH.toDouble(),
                    INPUT_HEIGHT.toDouble()
                )
            )

            val roiTime =
                SystemClock.elapsedRealtime() - roiStart

            Log.d(TAG, "Emotion ROI + resize = ${roiTime}ms")

            // ----------------------------------------------------
            // 5. Build FLOAT32 [1,48,48,1]
            //
            // Python:
            //
            // x = x.astype("float32") / 255.0
            // ----------------------------------------------------

            val input =
                ByteBuffer.allocateDirect(
                    INPUT_WIDTH *
                            INPUT_HEIGHT *
                            4
                ).apply {
                    order(
                        ByteOrder.nativeOrder()
                    )
                }

            val pixels =
                ByteArray(
                    INPUT_WIDTH *
                            INPUT_HEIGHT
                )

            resized.get(
                0,
                0,
                pixels
            )

            for (pixel in pixels) {

                val value =
                    (
                            pixel.toInt() and
                                    0xFF
                            ).toFloat() /
                            255.0f

                input.putFloat(
                    value
                )
            }

            input.rewind()

            // ----------------------------------------------------
            // 6. Run TFLite
            // ----------------------------------------------------

            val inferenceStart = SystemClock.elapsedRealtime()

            val outputShape =
                interpreter
                    .getOutputTensor(0)
                    .shape()

            val prediction =
                when {

                    outputShape.contentEquals(
                        intArrayOf(1, 7)
                    ) -> {

                        val output =
                            Array(1) {
                                FloatArray(7)
                            }

                        interpreter.run(
                            input,
                            output
                        )

                        output[0]
                    }

                    outputShape.contentEquals(
                        intArrayOf(7)
                    ) -> {

                        val output =
                            FloatArray(7)

                        interpreter.run(
                            input,
                            output
                        )

                        output
                    }

                    else -> {

                        throw IllegalStateException(
                            "Unsupported emotion output shape: " +
                                    outputShape.contentToString()
                        )
                    }
                }

            val inferenceTime =
                SystemClock.elapsedRealtime() - inferenceStart

            Log.d(TAG, "Emotion TFLite = ${inferenceTime}ms")

            Log.d(
                TAG,
                "Raw emotion output = " +
                        prediction.contentToString()
            )

            // ----------------------------------------------------
            // 7. Extract required classes
            // ----------------------------------------------------
            val postStart = SystemClock.elapsedRealtime()

            val happy =
                prediction[
                    HAPPY_INDEX
                ]

            val neutral =
                prediction[
                    NEUTRAL_INDEX
                ]

            val surprise =
                prediction[
                    SURPRISE_INDEX
                ]

            val total =
                happy +
                        neutral +
                        surprise

            // ----------------------------------------------------
            // 8. Renormalize
            // ----------------------------------------------------

            val baseEmotion =
                if (total <= 0.000001f) {

                    "neutral"

                } else {

                    val happyProbability =
                        happy / total

                    val neutralProbability =
                        neutral / total

                    val surpriseProbability =
                        surprise / total

                    when {

                        happyProbability >=
                                neutralProbability &&
                                happyProbability >=
                                surpriseProbability -> {

                            "happy"
                        }

                        neutralProbability >=
                                happyProbability &&
                                neutralProbability >=
                                surpriseProbability -> {

                            "neutral"
                        }

                        else -> {

                            "surprise"
                        }
                    }
                }

            Log.d(
                TAG,
                "Emotion base result → $baseEmotion"
            )

            // ----------------------------------------------------
            // 8b. Emotion post-processing timing
            // ----------------------------------------------------
            val postTime =
                SystemClock.elapsedRealtime() - postStart

            Log.d(TAG, "Emotion postprocessing = ${postTime}ms")

            // ----------------------------------------------------
            // 9. Hand-on-chin
            // ----------------------------------------------------
            val handStart = SystemClock.elapsedRealtime()

            val handOnChin =
                detectHandOnChin(
                    faceCrop,
                    faceRect
                )

            val handTime =
                SystemClock.elapsedRealtime() - handStart

            Log.d(TAG, "Emotion hand detection = ${handTime}ms")

            Log.d(
                TAG,
                "Hand on chin → $handOnChin"
            )

            // ----------------------------------------------------
            // 10. Confuse rule
            // ----------------------------------------------------

            val finalEmotion =
                if (
                    handOnChin &&
                    baseEmotion != "happy"
                ) {

                    "confuse"

                } else {

                    baseEmotion
                }

            val totalTime =
                SystemClock.elapsedRealtime() - totalStart

            Log.i(
                TAG,
                "EMOTION RESULT → $finalEmotion"
            )

            Log.i(TAG, "Emotion TOTAL = ${totalTime}ms")

            return finalEmotion

        } catch (e: Throwable) {

            val totalTime =
                SystemClock.elapsedRealtime() - totalStart


            Log.e(
                TAG,
                "Emotion inference failed after ${totalTime}ms",
                e
            )

            return null

        } finally {

            try {
                resized?.release()
            } catch (ignored: Throwable) {
            }

            try {
                roi?.release()
            } catch (ignored: Throwable) {
            }

            try {
                grayEq?.release()
            } catch (ignored: Throwable) {
            }

            try {
                gray?.release()
            } catch (ignored: Throwable) {
            }
        }
    }

    // ============================================================
    // BITMAP -> GRAYSCALE MAT
    // ============================================================

    private fun bitmapToGrayMat(
        bitmap: Bitmap
    ): Mat {

        val sourceBitmap =
            if (
                bitmap.config ==
                Bitmap.Config.ARGB_8888
            ) {

                bitmap

            } else {

                Bitmap.createBitmap(
                    bitmap.width,
                    bitmap.height,
                    Bitmap.Config.ARGB_8888
                ).also { converted ->

                    Canvas(converted)
                        .drawBitmap(
                            bitmap,
                            0f,
                            0f,
                            null
                        )
                }
            }

        val width =
            sourceBitmap.width

        val height =
            sourceBitmap.height

        val pixels =
            IntArray(
                width *
                        height
            )

        sourceBitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        /*
         * OpenCV Mat in RGBA format.
         */
        val rgba =
            ByteArray(
                width *
                        height *
                        4
            )

        var index = 0

        for (pixel in pixels) {

            rgba[index++] =
                Color.red(pixel)
                    .toByte()

            rgba[index++] =
                Color.green(pixel)
                    .toByte()

            rgba[index++] =
                Color.blue(pixel)
                    .toByte()

            rgba[index++] =
                255.toByte()
        }

        val rgbaMat =
            Mat(
                height,
                width,
                CvType.CV_8UC4
            )

        rgbaMat.put(
            0,
            0,
            rgba
        )

        val grayMat =
            Mat()

        Imgproc.cvtColor(
            rgbaMat,
            grayMat,
            Imgproc.COLOR_RGBA2GRAY
        )

        rgbaMat.release()

        if (
            sourceBitmap !== bitmap
        ) {

            try {
                sourceBitmap.recycle()
            } catch (ignored: Throwable) {
            }
        }

        return grayMat
    }

    // ============================================================
    // HAAR FACE DETECTION
    // ============================================================

    private fun findLargestFace(
        grayEq: Mat
    ): Rect? {

        val cascade =
            faceCascade
                ?: return null

        val faces =
            MatOfRect()

        try {

            cascade.detectMultiScale(
                grayEq,
                faces,
                1.1,
                3,
                0,
                Size(
                    30.0,
                    30.0
                ),
                Size()
            )

            /*
             * IMPORTANT:
             *
             * MatOfRect must be converted to
             * Array<Rect> using toArray().
             */
            val rectangles =
                faces.toArray()

            Log.d(
                TAG,
                "Haar input → ${grayEq.cols()}x${grayEq.rows()}"
            )

            Log.d(
                TAG,
                "Haar detected face count → ${rectangles.size}"
            )

            if (
                rectangles.isEmpty()
            ) {

                return null
            }

            /*
             * Python chooses the largest face:
             *
             * max(faces, key=lambda f: f[2] * f[3])
             */
            var largest =
                rectangles[0]

            var largestArea =
                largest.area().toDouble()

            for (rect in rectangles) {

                val area =
                    rect.area().toDouble()

                if (
                    area > largestArea
                ) {

                    largest =
                        rect

                    largestArea =
                        area
                }
            }

            return largest

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Haar face detection failed",
                e
            )

            return null

        } finally {

            faces.release()
        }
    }

    // ============================================================
    // HAND-ON-CHIN DETECTION
    // ============================================================

    private fun detectHandOnChin(
        bitmap: Bitmap,
        faceRect: Rect?
    ): Boolean {

        val landmarker =
            handLandmarker
                ?: return false

        try {

            val image =
                BitmapImageBuilder(
                    bitmap
                ).build()

            /*
             * VIDEO mode requires increasing
             * timestamps.
             */
            val now =
                System.currentTimeMillis()

            handTimestampMs =
                maxOf(
                    handTimestampMs + 1L,
                    now
                )

            val result =
                landmarker.detectForVideo(
                    image,
                    handTimestampMs
                )

            val hands =
                result.landmarks()

            if (
                hands.isEmpty()
            ) {

                return false
            }

            // ----------------------------------------------------
            // Face width
            // ----------------------------------------------------

            val faceWidth: Double =
                if (
                    faceRect != null
                ) {

                    faceRect.width.toDouble()

                } else {

                    bitmap.width.toDouble()
                }

            // ----------------------------------------------------
            // Approximate chin location
            // ----------------------------------------------------

            val chinX: Double =
                if (
                    faceRect != null
                ) {

                    faceRect.x.toDouble() +
                            faceRect.width.toDouble() /
                            2.0

                } else {

                    bitmap.width.toDouble() /
                            2.0
                }

            val chinY: Double =
                if (
                    faceRect != null
                ) {

                    faceRect.y.toDouble() +
                            faceRect.height.toDouble()

                } else {

                    bitmap.height.toDouble()
                }

            // ----------------------------------------------------
            // Distance threshold
            // ----------------------------------------------------

            val threshold: Double =
                faceWidth *
                        HAND_CHIN_DISTANCE_RATIO

            // ----------------------------------------------------
            // Check hand landmarks
            // ----------------------------------------------------

            for (
            hand in hands
            ) {

                for (
                index in HAND_KEY_POINTS
                ) {

                    if (
                        index < 0 ||
                        index >= hand.size
                    ) {
                        continue
                    }

                    val landmark =
                        hand[index]

                    val px: Double =
                        landmark.x()
                            .toDouble() *
                                bitmap.width

                    val py: Double =
                        landmark.y()
                            .toDouble() *
                                bitmap.height

                    val dx: Double =
                        px -
                                chinX

                    val dy: Double =
                        py -
                                chinY

                    val distance: Double =
                        sqrt(
                            dx * dx +
                                    dy * dy
                        )

                    if (
                        distance <
                        threshold
                    ) {

                        Log.d(
                            TAG,
                            "HAND NEAR CHIN → " +
                                    "distance=$distance " +
                                    "threshold=$threshold"
                        )

                        return true
                    }
                }
            }

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Hand-on-chin detection failed",
                e
            )
        }

        return false
    }

    // ============================================================
    // LOAD HAAR CASCADE
    // ============================================================

    private fun loadCascade(
        context: Context,
        assetPath: String
    ): CascadeClassifier? {

        return try {

            val input =
                context.assets.open(
                    assetPath
                )

            val cascadeDirectory =
                context.getDir(
                    "emotion_cascade",
                    Context.MODE_PRIVATE
                )

            val cascadeFile =
                File(
                    cascadeDirectory,
                    "haarcascade_frontalface_default.xml"
                )

            FileOutputStream(
                cascadeFile
            ).use { output ->

                val buffer =
                    ByteArray(
                        4096
                    )

                while (true) {

                    val count =
                        input.read(
                            buffer
                        )

                    if (
                        count <= 0
                    ) {
                        break
                    }

                    output.write(
                        buffer,
                        0,
                        count
                    )
                }
            }

            input.close()

            val classifier =
                CascadeClassifier(
                    cascadeFile.absolutePath
                )

            if (
                classifier.empty()
            ) {

                Log.e(
                    TAG,
                    "Loaded Haar cascade is empty"
                )

                /*
                 * Do NOT call classifier.release().
                 *
                 * CascadeClassifier in this dependency
                 * does not expose that Kotlin method.
                 */
                null

            } else {

                classifier
            }

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Failed to load Haar cascade",
                e
            )

            null
        }
    }

    // ============================================================
    // LOAD TFLITE MODEL
    // ============================================================

    private fun loadModelFile(
        context: Context,
        assetPath: String
    ): MappedByteBuffer {

        val descriptor =
            context.assets.openFd(
                assetPath
            )

        FileInputStream(
            descriptor.fileDescriptor
        ).use { inputStream ->

            return inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }

    // ============================================================
    // CLOSE
    // ============================================================

    fun close() {

        try {

            handLandmarker?.close()

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "HandLandmarker close failed",
                e
            )
        }

        try {

            interpreter.close()

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Emotion interpreter close failed",
                e
            )
        }

        Log.d(
            TAG,
            "EmotionPredictor CLOSED"
        )
    }
}