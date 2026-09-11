package com.example.camera_person_detetction_poc.camera

import android.util.Log
import com.example.camera_person_detetction_poc.ai.ObjectDetectorManager

class AiFrameConsumer(
    private val objectDetectorManager: ObjectDetectorManager
) : FrameConsumer {
    
    override fun onFrame(frame: CameraFrame) {

        try {

            Log.d(
                "CameraPOC",
                "AI Consumer received frame: " +
                        "${frame.width}x${frame.height}"
            )

            val bitmap = YuvUtils.nv21ToBitmap(
                frame.data,
                frame.width,
                frame.height
            )

            objectDetectorManager.processBitmap(
                bitmap,
                "camera"
            )

        } catch (e: Exception) {

            Log.e(
                "CameraPOC",
                "AI frame processing failed",
                e
            )
        }
    }


}
