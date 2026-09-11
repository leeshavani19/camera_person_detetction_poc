package com.example.camera_person_detetction_poc.camera
import android.util.Log

class FrameDistributor {


    private val consumers = mutableListOf<FrameConsumer>()

    fun addConsumer(consumer: FrameConsumer) {
        Log.d( "Consumer added", "" )
        consumers.add(consumer)
    }

    fun removeConsumer(consumer: FrameConsumer) {
        consumers.remove(consumer)
    }

    fun distribute(frame: CameraFrame) {
        Log.d( "CameraPOC", "Distributing frame to ${consumers.size} consumers" )
        consumers.forEach { consumer ->
            consumer.onFrame(frame)
        }
    }


}
