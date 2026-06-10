package com.kmu_focus.focusandroid.core.metadata.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class MetadataDocumentWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun write(json: String): Boolean = runCatching {
        val outputDir = File(context.noBackupFilesDir, METADATA_DIR).apply { mkdirs() }
        val fileName = "metadata_${System.currentTimeMillis()}.json"
        File(outputDir, fileName).writeText(json, Charsets.UTF_8)
        true
    }.getOrDefault(false)

    private companion object {
        const val METADATA_DIR = "metadata"
    }
}
