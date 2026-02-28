package com.privacyshield.security

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.nio.ByteBuffer

private const val TAG = "MemoryGuard"

object MemoryGuard {

    private var lowMemoryCallback: (() -> Unit)? = null

    fun startMemoryMonitor(context: Context) {
        // Register implicit low-memory callbacks via Application overrides
        Log.i(TAG, "Memory monitor initialised")
    }

    fun registerLowMemoryCallback(cb: () -> Unit) {
        lowMemoryCallback = cb
    }

    fun onLowMemory() {
        Log.w(TAG, "Low memory event — triggering cleanup")
        lowMemoryCallback?.invoke()
    }

    /** Zero-fill a ByteBuffer before releasing it to GC. */
    fun secureClear(buffer: ByteBuffer) {
        val saved = buffer.position()
        buffer.clear()
        repeat(buffer.capacity()) { buffer.put(it, 0) }
        buffer.position(saved)
    }

    /** Zero-fill a byte array. */
    fun secureClear(array: ByteArray) = array.fill(0)

    /** Zero-fill a char array (e.g., password buffers). */
    fun secureClear(array: CharArray) = array.fill('\u0000')

    fun getAvailableMemoryMB(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / 1_048_576
    }

    fun isLowMemory(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.lowMemory
    }
}
