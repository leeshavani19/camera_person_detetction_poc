package com.example.camera_person_detetction_poc.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraManager(
    private val activity: Activity

) {

    companion object {
        private const val TAG = "CameraPOC"
        private const val CAMERA_ID = "0"

        private const val WIDTH = 640
        private const val HEIGHT = 480

        private const val IMAGE_READER_BUFFER_SIZE = 3
    }

    private val cameraManager: AndroidCameraManager =
        activity.getSystemService(Context.CAMERA_SERVICE)
                as AndroidCameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler

    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    private val frameDistributor = FrameDistributor()

    fun addFrameConsumer(consumer: FrameConsumer) {
        frameDistributor.addConsumer(consumer)
    }

    fun removeFrameConsumer(consumer: FrameConsumer) {
        frameDistributor.removeConsumer(consumer)
    }



    fun start() {

        Log.d(TAG, "Starting camera")

        startCameraThread()

        if (
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        try {

            val characteristics =
                cameraManager.getCameraCharacteristics(CAMERA_ID)

            val capabilities =
                characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                )

            Log.d(
                TAG,
                "Camera capabilities: ${capabilities?.contentToString()}"
            )

            imageReader = ImageReader.newInstance(
                WIDTH,
                HEIGHT,
                ImageFormat.YUV_420_888,
                IMAGE_READER_BUFFER_SIZE
            )

            imageReader?.setOnImageAvailableListener(
                { reader ->


                    val image = reader.acquireLatestImage()

                    if (image != null) {

                        frameCount++

                        val currentTime =
                            System.currentTimeMillis()

                        if (currentTime - lastFpsTime >= 1000) {

                            Log.d(
                                TAG,
                                "Camera FPS: $frameCount"
                            )

                            frameCount = 0
                            lastFpsTime = currentTime
                        }

                        Log.d(
                            TAG,
                            "Frame received: " +
                                    "${image.width}x${image.height}"
                        )

                        val nv21 = YuvUtils.yuv420ToNv21(image)

                        val frame = CameraFrame(
                            data = nv21,
                            width = image.width,
                            height = image.height,
                            timestamp = image.timestamp
                        )

                        frameDistributor.distribute(frame)

                        image.close()


                        frameDistributor.distribute(frame)

                        image.close()


                    }


                },
                cameraHandler
            )

            cameraManager.openCamera(
                CAMERA_ID,
                cameraStateCallback,
                cameraHandler
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start camera",
                e
            )
        }
    }

    private val cameraStateCallback =
        object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {

                Log.d(
                    TAG,
                    "Camera opened successfully"
                )

                cameraDevice = camera

                createCaptureSession(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {

                Log.e(
                    TAG,
                    "Camera disconnected"
                )

                camera.close()

                cameraDevice = null
            }

            override fun onError(
                camera: CameraDevice,
                error: Int
            ) {

                Log.e(
                    TAG,
                    "Camera error: $error"
                )

                camera.close()

                cameraDevice = null
            }
        }

    private fun createCaptureSession(
        camera: CameraDevice
    ) {

        val surface =
            imageReader?.surface
                ?: return

        try {

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(
                        session: CameraCaptureSession
                    ) {

                        Log.d(
                            TAG,
                            "Capture session configured"
                        )

                        captureSession = session

                        val request =
                            camera.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW
                            ).apply {

                                addTarget(surface)
                            }

                        session.setRepeatingRequest(
                            request.build(),
                            null,
                            cameraHandler
                        )

                        Log.d(
                            TAG,
                            "Camera streaming started"
                        )
                    }

                    override fun onConfigureFailed(
                        session: CameraCaptureSession
                    ) {

                        Log.e(
                            TAG,
                            "Capture session configuration failed"
                        )
                    }
                },
                cameraHandler
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create capture session",
                e
            )
        }
    }

    private fun startCameraThread() {

        cameraThread =
            HandlerThread("CameraPOCThread")

        cameraThread.start()

        cameraHandler =
            Handler(cameraThread.looper)
    }

    fun stop() {

        Log.d(
            TAG,
            "Stopping camera"
        )

        try {

            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error while stopping camera",
                e
            )
        }

        if (::cameraThread.isInitialized) {

            cameraThread.quitSafely()

            try {
                cameraThread.join()
            } catch (e: InterruptedException) {
                Log.e(
                    TAG,
                    "Camera thread interrupted",
                    e
                )
            }
        }
    }
}