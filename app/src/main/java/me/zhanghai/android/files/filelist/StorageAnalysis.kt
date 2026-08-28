/*
 * Copyright (c) 2026 Material Files Batonz contributors
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Files
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.getFileStore
import me.zhanghai.android.files.provider.common.newDirectoryStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** A result deliberately distinguishes an exact value from a partial or unavailable value. */
data class StorageAnalysisResult(
    val size: Long?,
    val storageReference: Long?,
    val isPartial: Boolean = false,
    val isCalculating: Boolean = false
) {
    val percentage: Double?
        get() = if (size != null && storageReference != null && storageReference > 0) {
            size.toDouble() * 100.0 / storageReference
        } else null
}

class StorageAnalysisTask(
    private val path: Path,
    private val files: List<FileItem>,
    private val callback: (Map<Path, StorageAnalysisResult>) -> Unit
) {
    private var future: Future<*>? = null

    fun start() {
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit {
            val reference = try {
                path.getFileStore().totalSpace.takeIf { it > 0 }
            } catch (_: Exception) {
                null
            }
            val result = files.associate { file ->
                file.path to if (!file.attributes.isDirectory) {
                    StorageAnalysisResult(file.attributes.size(), reference)
                } else {
                    val directory = calculateDirectorySize(file.path)
                    StorageAnalysisResult(directory.size, reference, directory.partial)
                }
            }
            if (!Thread.currentThread().isInterrupted) callback(result)
        }
    }

    fun cancel() {
        future?.cancel(true)
    }

    private data class DirectoryResult(val size: Long, val partial: Boolean)

    private fun calculateDirectorySize(root: Path): DirectoryResult {
        val pending = ArrayDeque<Path>()
        pending.add(root)
        var total = 0L
        var partial = false
        while (pending.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted) return DirectoryResult(total, true)
            val directory = pending.removeLast()
            try {
                directory.newDirectoryStream().use { children ->
                    for (child in children) {
                        if (Thread.currentThread().isInterrupted) return DirectoryResult(total, true)
                        try {
                            val attrs = Files.readAttributes(
                                child, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
                            )
                            when {
                                attrs.isSymbolicLink -> Unit
                                attrs.isDirectory -> pending.add(child)
                                else -> total = total.saturatingPlus(attrs.size())
                            }
                        } catch (_: IOException) {
                            partial = true
                        } catch (_: DirectoryIteratorException) {
                            partial = true
                        } catch (_: SecurityException) {
                            partial = true
                        }
                    }
                }
            } catch (_: IOException) {
                partial = true
            } catch (_: SecurityException) {
                partial = true
            }
        }
        return DirectoryResult(total, partial)
    }

    private fun Long.saturatingPlus(value: Long): Long =
        if (Long.MAX_VALUE - this < value) Long.MAX_VALUE else this + value
}
