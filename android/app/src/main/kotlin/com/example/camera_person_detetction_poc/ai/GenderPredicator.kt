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

class GenderPredictor(
    context: Context,
    modelAsset: String = "models/gender_model_v2.tflite"
) {

    private val tag = "GenderPredictor"

    private val interpreter: Interpreter

    private val inputWidth: Int
    private val inputHeight: Int

    init {
        try {

            // Load TFLite model from assets
            val modelBuffer = loadModelFile(
                context,
                modelAsset
            )

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }

            interpreter = Interpreter(
                modelBuffer,
                options
            )

            // ---------------------------------------------------------
            // INPUT / OUTPUT TENSORS
            // ---------------------------------------------------------

            val inputTensor =
                interpreter.getInputTensor(0)

            val outputTensor =
                interpreter.getOutputTensor(0)

            val inputShape =
                inputTensor.shape()

            val inputType =
                inputTensor.dataType()

            val outputShape =
                outputTensor.shape()

            val outputType =
                outputTensor.dataType()

            Log.d(
                tag,
                "Input shape = ${inputShape.contentToString()}"
            )

            Log.d(
                tag,
                "Input type = $inputType"
            )

            Log.d(
                tag,
                "Output shape = ${outputShape.contentToString()}"
            )

            Log.d(
                tag,
                "Output type = $outputType"
            )

            // ---------------------------------------------------------
            // INPUT SHAPE
            // Expected:
            //
            // [1, 224, 224, 3]
            // ---------------------------------------------------------

            if (
                inputShape.size == 4 &&
                inputShape[3] == 3
            ) {

                inputHeight =
                    inputShape[1]

                inputWidth =
                    inputShape[2]

            } else {

                throw IllegalArgumentException(
                    "Unexpected gender model input shape: " +
                            inputShape.contentToString()
                )
            }

            // ---------------------------------------------------------
            // INPUT TYPE
            // ---------------------------------------------------------

            if (inputType != DataType.FLOAT32) {

                throw IllegalArgumentException(
                    "Unsupported gender model input type: $inputType"
                )
            }

            // ---------------------------------------------------------
            // OUTPUT
            //
            // Expected:
            //
            // [1, 1]
            // FLOAT32
            // ---------------------------------------------------------

            if (outputShape.size != 2 ||
                outputShape[0] != 1 ||
                outputShape[1] != 1
            ) {

                throw IllegalArgumentException(
                    "Unexpected gender model output shape: " +
                            outputShape.contentToString()
                )
            }

            if (outputType != DataType.FLOAT32) {

                throw IllegalArgumentException(
                    "Unsupported gender model output type: $outputType"
                )
            }

            Log.i(
                tag,
                "Gender model loaded successfully " +
                        "${inputWidth}x${inputHeight}"
            )

        } catch (e: Exception) {

            Log.e(
                tag,
                "Gender model initialization FAILED",
                e
            )

            throw RuntimeException(
                "Unable to initialize gender model",
                e
            )
        }
    }

    // -------------------------------------------------------------
    // LOAD MODEL
    // -------------------------------------------------------------

    private fun loadModelFile(
        context: Context,
        modelAsset: String
    ): MappedByteBuffer {

        val fileDescriptor =
            context.assets.openFd(modelAsset)

        val inputStream =
            FileInputStream(
                fileDescriptor.fileDescriptor
            )

        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    // -------------------------------------------------------------
    // PREDICT
    // -------------------------------------------------------------

    fun predict(
        bitmap: Bitmap
    ): GenderResult {

        Log.d(
            tag,
            "Running gender inference"
        )

        val totalStart = SystemClock.elapsedRealtime()

        // ---------------------------------------------------------
        // 1. PREPROCESSING
        // ---------------------------------------------------------
        val preprocessStart = SystemClock.elapsedRealtime()

        val input =
            preprocess(bitmap)

        val preprocessTime =
            SystemClock.elapsedRealtime() - preprocessStart

        Log.d(tag, "Gender preprocessing = ${preprocessTime}ms")

        // ---------------------------------------------------------
        // 2. TFLITE INFERENCE
        // ---------------------------------------------------------
        val inferenceStart = SystemClock.elapsedRealtime()

        val output =
            Array(1) {
                FloatArray(1)
            }

        interpreter.run(
            input,
            output
        )

        val inferenceTime =
            SystemClock.elapsedRealtime() - inferenceStart

        Log.d(tag, "Gender TFLite = ${inferenceTime}ms")

        // ---------------------------------------------------------
        // 3. POST PROCESSING
        // ---------------------------------------------------------
        val postStart = SystemClock.elapsedRealtime()

        val raw =
            output[0][0]

        Log.d(
            tag,
            "Raw gender output = $raw"
        )

        // ---------------------------------------------------------
        // Python:
        //
        // gender = "Male" if conf > 0.5 else "Female"
        // ---------------------------------------------------------

        val gender =
            if (raw > 0.5f) {
                "Male"
            } else {
                "Female"
            }

        val confidence =
            if (gender == "Male") {
                raw
            } else {
                1f - raw
            }

        val postTime =
            SystemClock.elapsedRealtime() - postStart

        val totalTime =
            SystemClock.elapsedRealtime() - totalStart

        Log.d(tag, "Gender postprocessing = ${postTime}ms")

        Log.i(
            tag,
            "Prediction → " +
                    "$gender, " +
                    "confidence=${"%.4f".format(confidence)}, " +
                    "raw=${"%.4f".format(raw)}"
        )

        Log.i(tag, "Gender TOTAL = ${totalTime}ms")

        return GenderResult(
            gender = gender,
            confidence = confidence
        )
    }

    // -------------------------------------------------------------
    // PREPROCESS
    // -------------------------------------------------------------

    private fun preprocess(
        bitmap: Bitmap
    ): ByteBuffer {

        val resized =
            Bitmap.createScaledBitmap(
                bitmap,
                inputWidth,
                inputHeight,
                true
            )

        val inputBuffer =
            ByteBuffer.allocateDirect(
                4 *
                        inputWidth *
                        inputHeight *
                        3
            ).apply {
                order(ByteOrder.nativeOrder())
            }

        val pixels =
            IntArray(
                inputWidth *
                        inputHeight
            )

        resized.getPixels(
            pixels,
            0,
            inputWidth,
            0,
            0,
            inputWidth,
            inputHeight
        )

        for (pixel in pixels) {

            // Android Bitmap = ARGB
            //
            // Extract RGB values.
            //
            // Python does:
            //
            // BGR -> RGB
            //
            // Android Bitmap is already RGB,
            // so no additional channel conversion
            // is required here.

            val r =
                ((pixel shr 16) and 0xFF)
                    .toFloat()

            val g =
                ((pixel shr 8) and 0xFF)
                    .toFloat()

            val b =
                (pixel and 0xFF)
                    .toFloat()

            // EfficientNet input representation:
            // 0..255 FLOAT32

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        inputBuffer.rewind()

        return inputBuffer
    }

    // -------------------------------------------------------------
    // CLOSE
    // -------------------------------------------------------------

    fun close() {

        Log.d(
            tag,
            "Closing GenderPredictor"
        )

        interpreter.close()
    }
}

data class GenderResult(
    val gender: String,
    val confidence: Float
)