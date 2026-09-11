package com.example.camera_person_detetction_poc.ai
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ObjectDetectorManager(private val context: Context) {

    private var mpThread: HandlerThread? = null
    private var mpHandler: Handler? = null
    private var detector: ObjectDetector? = null

    @Volatile
    private var isReady = false

    private val isBusy = AtomicBoolean(false)
    private val TAG = "ObjectDetectorManager"
    private val tagByTimestamp = ConcurrentHashMap<Long, String>()
    private val bitmapByTimestamp = ConcurrentHashMap<Long, Bitmap>()


    @Volatile
    var differentPersonDetected = false

    var uniquePersonId = -1

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1

        const val DEFAULT_SCORE_THRESHOLD = 0.5f
        const val DEFAULT_MAX_RESULTS = 50
    }

    fun init(
        modelAsset: String = "models/efficientdet_lite2.task",
        scoreThreshold: Float = 0.80f,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        currentDelegate: Int = DELEGATE_CPU,
        onResult: (ObjectDetectorResult, String,Bitmap) -> Unit,
        onError: (RuntimeException) -> Unit = {}
    ) {
        Log.d(
            TAG,
            "init() modelAsset=$modelAsset delegate=$currentDelegate score=$scoreThreshold max=$maxResults"
        )



        if (mpThread == null) {
            mpThread = HandlerThread("mp-object-task-thread").apply { start() }
            mpHandler = Handler(mpThread!!.looper)
            Log.d(TAG, "HandlerThread created")
        } else {
            Log.d(TAG, "HandlerThread already exists")
        }

        val baseBuilder = BaseOptions.builder().setModelAssetPath(modelAsset)
        when (currentDelegate) {
            DELEGATE_CPU -> baseBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseBuilder.setDelegate(Delegate.GPU)
        }
        val base = baseBuilder.build()

        val opts = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(if (maxResults <= 0) 1 else maxResults)
            .setRunningMode(RunningMode.LIVE_STREAM)

            .setResultListener { result: ObjectDetectorResult, _: MPImage ->
                val ts = result.timestampMs()
                val tag = tagByTimestamp.remove(ts) ?: "unknown"
                val sourceBitmap = bitmapByTimestamp.remove(ts)


                // Main summary log
                Log.i(TAG, "Object Detection → ${result.detections().size} objects | camera=$tag")

                // Detailed logs for each detection
                result.detections().forEachIndexed { index, detection ->
                    val category = detection.categories().firstOrNull()
                    val label = category?.categoryName() ?: "unknown"
                    val score = category?.score() ?: -1f

                    val box = detection.boundingBox()
                    Log.i(
                        TAG,
                        " #$index  label=$label  score=${"%.2f".format(score)}  " +
                                "box=[${box.left}, ${box.top}, ${box.right}, ${box.bottom}]"
                    )
                }

                val person = result.detections()
                        .firstOrNull { detection ->
                            detection
                                .categories()
                                .firstOrNull()
                                ?.categoryName()
                                ?.equals(
                                    "person",
                                    ignoreCase = true
                                ) == true
                        }


                if (person != null) {
                    val newBox = person.boundingBox()





                    if (LastPersonBoxTracker.isSamePerson(newBox)) {
                        // SAME PERSON → KEEP SAME ID
                        differentPersonDetected = false
                        Log.i(TAG, "Same person detected → ID=${LastPersonBoxTracker.currentId}, differentPersonDetected=$differentPersonDetected")
                    } else {
                        // NEW PERSON → ASSIGN NEW ID
                        val newId = PersonIdTracker.nextId()
                        LastPersonBoxTracker.update(newBox, newId)
                        differentPersonDetected = true
                        Log.i(TAG, "New unique person detected → ID=$newId, differentPersonDetected=$differentPersonDetected")
                    }
                } else {
                    // No person detected → reset state softly
                    LastPersonBoxTracker.noDetectionTick()
                    differentPersonDetected = false
                    Log.i(TAG, "No person detected → differentPersonDetected=$differentPersonDetected")
                }
                // --------------------------------------------------------------

                if (sourceBitmap != null) {
                    onResult(
                        result,
                        tag,
                        sourceBitmap
                    )
                }

                isBusy.set(false)

            }
            .setErrorListener { e -> onError(e) }
            .build()

        isReady = true
        mpHandler?.post {
            try {
                detector?.close()
                detector = ObjectDetector.createFromOptions(context, opts)
                Log.d(TAG, "ObjectDetector READY on thread: ${Thread.currentThread().name}")
            } catch (t: Throwable) {
                Log.e(TAG, "ObjectDetector creation failed: ${t.message}", t)
                onError(RuntimeException(t))
            }
        }
    }

    fun processBitmap(bitmap: Bitmap, cameraTag: String) {
        val h = mpHandler
        if (h == null || !isReady) {
            Log.w(
                TAG,
                "processBitmap before init/ready; dropping. isReady=$isReady handlerNull=${h == null}"
            )
            return
        }
        if (!isBusy.compareAndSet(false, true)) {
            return
        }
        val safeBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val ts = SystemClock.uptimeMillis()
        tagByTimestamp[ts] = cameraTag
        bitmapByTimestamp[ts] = safeBmp

        h.post {
            try {
                val mpImg = BitmapImageBuilder(safeBmp).build()
                detector?.detectAsync(mpImg, ts)
            } catch (t: Throwable) {
                Log.e(TAG, "detectAsync failed: ${t.message}", t)
                tagByTimestamp.remove(ts)
                bitmapByTimestamp.remove(ts)
                isBusy.set(false)
            }
        }
    }

    fun close() {
        Log.d(TAG, "close() → Disposing detector + thread")

        isReady = false
        isBusy.set(false)
        tagByTimestamp.clear()
        bitmapByTimestamp.clear()



        val d = detector
        if (d != null) {
            try {
                mpHandler?.post {
                    try { d.close() } catch (_: Exception) {}
                    detector = null
                    Log.d(TAG, "ObjectDetector closed")
                }
            } catch (_: Exception) {}
        } else {
            detector = null
        }


        mpHandler = null

        try {
            mpThread?.quitSafely()
        } catch (_: Exception) {}

        mpThread = null
    }
}

object PersonIdTracker {
    private var currentId = 0
    fun nextId(): Int {
        return currentId++
    }
}

object LastPersonBoxTracker {
    private var lastBox: android.graphics.RectF? = null
    private var lastSeenTime = 0L
    private val timeout = 2500L   // 2.5 seconds stable timeout
    var currentId = -1

    fun isSamePerson(newBox: android.graphics.RectF): Boolean {
        val now = System.currentTimeMillis()
        val old = lastBox ?: return false

        val iou = calculateIOU(old, newBox)

        val same = iou > 0.25f && (now - lastSeenTime) < timeout
        if (same) {
            // Refresh tracking for the same person
            lastBox = android.graphics.RectF(newBox)
            lastSeenTime = now
        }
        return same
    }

    fun update(newBox: android.graphics.RectF, id: Int) {
        lastBox = android.graphics.RectF(newBox)
        lastSeenTime = System.currentTimeMillis()
        currentId = id
    }

    fun noDetectionTick() {
        if (System.currentTimeMillis() - lastSeenTime > timeout) {
            lastBox = null
            currentId = -1
        }
    }

    private fun calculateIOU(a: android.graphics.RectF, b: android.graphics.RectF): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)

        val intersection = maxOf(0f, right - left) * maxOf(0f, bottom - top)
        if (intersection <= 0f) return 0f

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        return intersection / (areaA + areaB - intersection)
    }
}