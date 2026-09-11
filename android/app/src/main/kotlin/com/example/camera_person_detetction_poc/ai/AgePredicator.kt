package com.example.camera_person_detetction_poc.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class AgePredictor(
    context: Context,
    modelAsset: String = "models/age_model_newfp16.tflite"
) {

    companion object {
        private const val TAG = "AgePredictor"

        private const val IMG_SIZE = 224

        private const val AGE_MIN = 1
        private const val AGE_MAX = 95

        private const val NUM_AGE_BINS = 95

        private val MEAN = floatArrayOf(
            0.485f,
            0.456f,
            0.406f
        )

        private val STD = floatArrayOf(
            0.229f,
            0.224f,
            0.225f
        )

        private val CLS_NAMES = arrayOf(
            "child",
            "teen",
            "young_adult",
            "adult",
            "senior"
        )
    }

    private val interpreter: Interpreter

    private val inputIndex: Int

    private val ageOutputIndex: Int
    private val classOutputIndex: Int
    private val youngAdultOutputIndex: Int

    init {
        try {

            val modelBuffer =
                loadModelFile(
                    context,
                    modelAsset
                )

            val options =
                Interpreter.Options().apply {
                    setNumThreads(2)
                }

            interpreter =
                Interpreter(
                    modelBuffer,
                    options
                )

            // ====================================================
            // INPUT
            // ====================================================

            val inputTensor =
                interpreter.getInputTensor(0)

            val inputShape =
                inputTensor.shape()

            val inputType =
                inputTensor.dataType()

            Log.d(
                TAG,
                "Input shape = "
                        + inputShape.contentToString()
            )

            Log.d(
                TAG,
                "Input type = $inputType"
            )

            if (
                inputType !=
                DataType.FLOAT32
            ) {
                throw IllegalArgumentException(
                    "Unsupported age input type: "
                            + inputType
                )
            }

            inputIndex =
                inputTensor.index()

            // ====================================================
            // OUTPUTS
            // ====================================================

            val outputCount =
                interpreter
                    .outputTensorCount

            Log.d(
                TAG,
                "Output tensor count = "
                        + outputCount
            )

            if (outputCount < 3) {
                throw IllegalArgumentException(
                    "Age model must have 3 outputs"
                )
            }

            val output0 =
                interpreter.getOutputTensor(0)

            val output1 =
                interpreter.getOutputTensor(1)

            val output2 =
                interpreter.getOutputTensor(2)

            Log.d(
                TAG,
                "Output[0] shape = "
                        + output0.shape()
                    .contentToString()
            )

            Log.d(
                TAG,
                "Output[1] shape = "
                        + output1.shape()
                    .contentToString()
            )

            Log.d(
                TAG,
                "Output[2] shape = "
                        + output2.shape()
                    .contentToString()
            )

            if (
                output0.dataType() !=
                DataType.FLOAT32 ||
                output1.dataType() !=
                DataType.FLOAT32 ||
                output2.dataType() !=
                DataType.FLOAT32
            ) {
                throw IllegalArgumentException(
                    "Age model outputs must be FLOAT32"
                )
            }

            ageOutputIndex = 0

            classOutputIndex = 1

            youngAdultOutputIndex = 2

            Log.i(
                TAG,
                "Age model loaded successfully"
            )

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Age model initialization FAILED",
                e
            )

            throw RuntimeException(
                "Unable to initialize age model",
                e
            )
        }
    }

    // ============================================================
    // MODEL LOADING
    // ============================================================

    private fun loadModelFile(
        context: Context,
        modelAsset: String
    ): MappedByteBuffer {

        val descriptor =
            context.assets.openFd(
                modelAsset
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
    // PREDICT
    // ============================================================

    @Synchronized
    fun predict(
        bitmap: Bitmap
    ): AgeResult {

        Log.d(
            TAG,
            "Running age inference"
        )

        val totalStart = SystemClock.elapsedRealtime()

        // ------------------------------------------------------------
        // 1. PREPROCESSING
        // ------------------------------------------------------------
        val preprocessStart = SystemClock.elapsedRealtime()

        val input =
            preprocess(bitmap)

        val preprocessTime =
            SystemClock.elapsedRealtime() - preprocessStart

        Log.d(TAG, "Age preprocessing = ${preprocessTime}ms")

        // ------------------------------------------------------------
        // 2. OUTPUT ALLOCATION
        // ------------------------------------------------------------
        val allocationStart = SystemClock.elapsedRealtime()

        val ageOutput =
            Array(1) {
                FloatArray(
                    NUM_AGE_BINS
                )
            }

        val classOutput =
            Array(1) {
                FloatArray(
                    CLS_NAMES.size
                )
            }

        val youngAdultOutput =
            Array(1) {
                FloatArray(15)
            }

        // IMPORTANT:
        // runForMultipleInputsOutputs expects
        // OUTPUT ORDINALS: 0, 1, 2
        //
        // Do NOT use Tensor.index() here.

        val outputs =
            HashMap<Int, Any>()

        outputs[0] = ageOutput
        outputs[1] = classOutput
        outputs[2] = youngAdultOutput

        val allocationTime =
            SystemClock.elapsedRealtime() - allocationStart

        Log.d(TAG, "Age output allocation = ${allocationTime}ms")

        // ------------------------------------------------------------
        // 3. TFLITE INFERENCE
        // ------------------------------------------------------------
        val inferenceStart = SystemClock.elapsedRealtime()

        interpreter.runForMultipleInputsOutputs(
            arrayOf(input),
            outputs
        )

        val inferenceTime =
            SystemClock.elapsedRealtime() - inferenceStart

        Log.d(TAG, "Age TFLite = ${inferenceTime}ms")

        // ------------------------------------------------------------
        // 4. POST PROCESSING
        // ------------------------------------------------------------
        val postStart = SystemClock.elapsedRealtime()

        // ------------------------------------------------------------
        // AGE HEAD
        // ------------------------------------------------------------

        val ageProb =
            softmax(
                ageOutput[0]
            )

        val ageVector =
            FloatArray(
                NUM_AGE_BINS
            ) { index ->
                (index + AGE_MIN).toFloat()
            }

        var agePrediction = 0f

        for (i in ageProb.indices) {
            agePrediction +=
                ageProb[i] *
                        ageVector[i]
        }

        // ------------------------------------------------------------
        // CLASS HEAD
        // ------------------------------------------------------------

        val classProb =
            softmax(
                classOutput[0]
            )

        var classIndex = 0

        for (
        i in 1 until classProb.size
        ) {
            if (
                classProb[i] >
                classProb[classIndex]
            ) {
                classIndex = i
            }
        }

        val classConfidence =
            classProb[classIndex]

        val className =
            CLS_NAMES[classIndex]

        // ------------------------------------------------------------
        // YOUNG ADULT REFINEMENT
        // ------------------------------------------------------------

        if (classIndex == 2) {

            val yaProb =
                softmax(
                    youngAdultOutput[0]
                )

            var yaAge = 0f

            for (
            i in yaProb.indices
            ) {
                val age =
                    (i + 21).toFloat()

                yaAge +=
                    yaProb[i] *
                            age
            }

            agePrediction =
                0.6f * agePrediction +
                        0.4f * yaAge
        }

        agePrediction =
            agePrediction.coerceIn(
                AGE_MIN.toFloat(),
                AGE_MAX.toFloat()
            )

        val roundedAge =
            String.format(
                "%.1f",
                agePrediction
            ).toFloat()

        val postTime =
            SystemClock.elapsedRealtime() - postStart

        val totalTime =
            SystemClock.elapsedRealtime() - totalStart

        Log.d(TAG, "Age postprocessing = ${postTime}ms")

        Log.i(
            TAG,
            "AGE RESULT → "
                    + "class=$className "
                    + "confidence="
                    + "%.4f".format(classConfidence)
                    + " age=$roundedAge"
        )

        Log.i(TAG, "Age TOTAL = ${totalTime}ms")

        return AgeResult(
            ageClass = className,
            confidence = classConfidence,
            age = roundedAge
        )
    }
    // ============================================================
    // PREPROCESS
    //
    // Matches uploaded Python implementation:
    //
    // BGR -> RGB
    // resize 224x224
    // /255
    // (pixel - mean) / std
    // ============================================================

    private fun preprocess(
        bitmap: Bitmap
    ): ByteBuffer {

        val resized =
            Bitmap.createScaledBitmap(
                bitmap,
                IMG_SIZE,
                IMG_SIZE,
                true
            )

        val input =
            ByteBuffer.allocateDirect(
                4 *
                        IMG_SIZE *
                        IMG_SIZE *
                        3
            )

        input.order(
            ByteOrder.nativeOrder()
        )

        val pixels =
            IntArray(
                IMG_SIZE *
                        IMG_SIZE
            )

        resized.getPixels(
            pixels,
            0,
            IMG_SIZE,
            0,
            0,
            IMG_SIZE,
            IMG_SIZE
        )

        for (pixel in pixels) {

            val r =
                ((pixel shr 16) and 0xFF)
                    .toFloat() /
                        255f

            val g =
                ((pixel shr 8) and 0xFF)
                    .toFloat() /
                        255f

            val b =
                (pixel and 0xFF)
                    .toFloat() /
                        255f

            input.putFloat(
                (r - MEAN[0]) /
                        STD[0]
            )

            input.putFloat(
                (g - MEAN[1]) /
                        STD[1]
            )

            input.putFloat(
                (b - MEAN[2]) /
                        STD[2]
            )
        }

        input.rewind()

        return input
    }

    // ============================================================
    // SOFTMAX
    // ============================================================

    private fun softmax(
        values: FloatArray
    ): FloatArray {

        var max =
            values[0]

        for (i in 1 until values.size) {
            if (values[i] > max) {
                max = values[i]
            }
        }

        val expValues =
            FloatArray(
                values.size
            )

        var sum = 0f

        for (i in values.indices) {

            val value =
                exp(
                    (
                            values[i] -
                                    max
                            ).toDouble()
                ).toFloat()

            expValues[i] =
                value

            sum += value
        }

        for (i in expValues.indices) {
            expValues[i] /=
                sum
        }

        return expValues
    }

    // ============================================================
    // CLOSE
    // ============================================================

    fun close() {

        Log.d(
            TAG,
            "Closing AgePredictor"
        )

        interpreter.close()
    }
}

// ================================================================
// RESULT
// ================================================================

data class AgeResult(
    val ageClass: String,
    val confidence: Float,
    val age: Float
)