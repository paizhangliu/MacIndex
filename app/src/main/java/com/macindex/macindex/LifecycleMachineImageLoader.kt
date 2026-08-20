package com.macindex.macindex

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.macindex.macindex.catalog.Machine
import com.macindex.macindex.catalog.CatalogFormatException
import com.macindex.macindex.resources.MachineResourceLoader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface MachineImageReady {
    fun onReady(bitmap: Bitmap)
}

fun interface MachineImageFailure {
    fun onFailure(error: Exception)
}

/** Performs only bitmap I/O off-main; each view key owns an independent latest-wins request. */
class LifecycleMachineImageLoader(
    private val owner: LifecycleOwner,
    private val assets: AssetManager,
) {
    private var nextRequest = 0L
    private val generations = mutableMapOf<String, Long>()

    @MainThread
    fun load(
        viewKey: String,
        machine: Machine,
        requestedWidth: Int,
        requestedHeight: Int,
        ready: MachineImageReady,
        failure: MachineImageFailure,
    ) {
        val generation = ++nextRequest
        generations[viewKey] = generation
        owner.lifecycleScope.launch {
            // BitmapFactory is blocking and cannot be interrupted. Decode and transfer ownership
            // entirely inside NonCancellable contexts: returning a Bitmap across a cancelled
            // dispatcher boundary could otherwise discard the result before it can be recycled.
            withContext(Dispatchers.IO + NonCancellable) {
                var bitmap: Bitmap? = null
                var decodeFailure: CatalogFormatException? = null
                try {
                    val pictureAsset = MachineResourceLoader.pictureAsset(machine)
                    bitmap = BitmapLoadingHelper.decodeSampledBitmapFromAsset(
                        assets,
                        pictureAsset,
                        requestedWidth,
                        requestedHeight,
                    ) ?: throw CatalogFormatException(
                        "Unable to decode picture for ${machine.uid()} at $pictureAsset",
                    )
                } catch (error: IOException) {
                    decodeFailure = CatalogFormatException(
                        "Unable to open picture for ${machine.uid()}", error,
                    )
                } catch (error: CatalogFormatException) {
                    decodeFailure = error
                }
                withContext(Dispatchers.Main.immediate + NonCancellable) {
                    if (isCurrent(viewKey, generation)) {
                        try {
                            if (bitmap != null) {
                                // Ownership passes to the ImageView callback exactly once.
                                ready.onReady(bitmap!!)
                            } else {
                                failure.onFailure(decodeFailure
                                    ?: IllegalStateException(
                                        "Image decode failed without a cause",
                                    ))
                            }
                        } finally {
                            generations.remove(viewKey)
                        }
                    } else {
                        bitmap?.recycle()
                    }
                }
            }
        }
    }

    private fun isCurrent(viewKey: String, generation: Long): Boolean =
        generations[viewKey] == generation &&
            owner.lifecycle.currentState != Lifecycle.State.DESTROYED
}
