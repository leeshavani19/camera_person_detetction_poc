package com.example.camera_person_detetction_poc.camera

import android.util.Log

class TestFrameConsumer(
    private val name: String
) : FrameConsumer {


    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()

    override fun onFrame(frame: CameraFrame) {

        frameCount++

        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLogTime >= 1000) {

            Log.d(
                "CameraPOC",
                "$name FPS: $frameCount"
            )

            frameCount = 0
            lastLogTime = currentTime
        }
    }


}
