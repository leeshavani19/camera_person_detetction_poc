package com.example.camera_person_detetction_poc.camera

data class CameraFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val timestamp: Long
)
