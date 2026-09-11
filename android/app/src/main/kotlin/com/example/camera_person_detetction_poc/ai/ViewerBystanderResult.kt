package com.example.camera_person_detetction_poc.ai

data class ViewerBystanderResult(
    val state: String,
    val yaw: Float?,
    val pitch: Float?,
    val gazeX: Float?,
    val gazeY: Float?
)