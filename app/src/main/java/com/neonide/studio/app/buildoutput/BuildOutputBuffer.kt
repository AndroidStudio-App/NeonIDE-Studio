package com.neonide.studio.app.buildoutput

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Buffered build output broadcaster.
 *
 * Why:
 * - Build output can be extremely chatty.
 * - Updating UI (TextView/Editor) per-line causes lag.
 * - AndroidCodeStudio batches output + trims old lines.
 */
object BuildOutputBuffer {

    private const val FLUSH_DELAY_MS = 150L
    private const val MAX_CHARS = 700_000        // ~0.7MB text cap
    private const val TRIM_TO_CHARS = 550_000

    private val handler = Handler(Looper.getMainLooper())
    private val pending = StringBuilder(8_192)
    private val flushScheduled = AtomicBoolean(false)

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    /** Current cached output (bounded). */
    @Volatile
    private var cache: String = ""

    fun getSnapshot(): String = cache

    fun clear() {
        synchronized(pending) {
            pending.clear()
        }
        cache = ""
        // notify listeners so UI clears immediately
        listeners.forEach { it.invoke("") }
    }

    fun appendLine(line: String) {
        val msg = if (line.endsWith("\n")) line else "$line\n"
        synchronized(pending) {
            pending.append(msg)
        }
        scheduleFlush()
    }

    fun appendRaw(text: String) {
        synchronized(pending) {
            pending.append(text)
        }
        scheduleFlush()
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
        // Immediately send current state
        listener.invoke(cache)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    private fun scheduleFlush() {
        // This can be called from background threads (Gradle output reader).
        // Use atomic CAS so we always schedule at least one pending flush.
        if (!flushScheduled.compareAndSet(false, true)) return

        handler.postDelayed({
            flushScheduled.set(false)
            flush()
        }, FLUSH_DELAY_MS)
    }

    private fun flush() {
        val chunk: String = synchronized(pending) {
            if (pending.isEmpty()) return
            val out = pending.toString()
            pending.clear()
            out
        }

        // Update cache, then trim if needed
        var newCache = cache + chunk
        if (newCache.length > MAX_CHARS) {
            val trimmedCount = newCache.length - TRIM_TO_CHARS
            newCache = "... [trimmed $trimmedCount chars for performance] ...\n\n" + newCache.takeLast(TRIM_TO_CHARS)
        }
        cache = newCache

        // Broadcast full snapshot (UI can setText efficiently)
        listeners.forEach { it.invoke(newCache) }
    }
}
