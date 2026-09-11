package com.example.camera_person_detetction_poc.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

object YuvUtils {

    fun yuv420ToNv21(image: Image): ByteArray {

        require(image.format == ImageFormat.YUV_420_888) {
            "Expected YUV_420_888 image"
        }

        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)

        var outputIndex = 0

        // Y plane
        for (row in 0 until height) {

            val rowStart = row * yRowStride

            for (col in 0 until width) {

                nv21[outputIndex++] =
                    yBuffer.get(rowStart + col)
            }
        }

        // V + U planes
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {

            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride

            for (col in 0 until chromaWidth) {

                val uIndex =
                    uRowStart + col * uPixelStride

                val vIndex =
                    vRowStart + col * vPixelStride

                nv21[outputIndex++] =
                    vBuffer.get(vIndex)

                nv21[outputIndex++] =
                    uBuffer.get(uIndex)
            }
        }

        return nv21
    }

    fun nv21ToBitmap(
        nv21: ByteArray,
        width: Int,
        height: Int
    ): Bitmap {

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            width,
            height,
            null
        )

        val outputStream =
            ByteArrayOutputStream()

        yuvImage.compressToJpeg(
            Rect(0, 0, width, height),
            100,
            outputStream
        )

        val jpegBytes =
            outputStream.toByteArray()

        return BitmapFactory.decodeByteArray(
            jpegBytes,
            0,
            jpegBytes.size
        )
    }


}
