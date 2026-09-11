package com.example.camera_person_detetction_poc.ai

import android.content.Context
import java.io.File

object AssetModelLoader {

    fun loadModelFile(
        context: Context,
        assetPath: String
    ): File {

        val fileName =
            assetPath.substringAfterLast("/")

        val modelFile =
            File(
                context.filesDir,
                fileName
            )

        if (!modelFile.exists()) {

            context.assets
                .open(assetPath)
                .use { input ->

                    modelFile.outputStream()
                        .use { output ->

                            input.copyTo(output)
                        }
                }
        }

        return modelFile
    }
}