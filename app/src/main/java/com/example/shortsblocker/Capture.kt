package com.example.shortsblocker

import android.os.SystemClock

/**
 * Diagnostic helper. Records which kinds of views (class name + view id only, never any
 * text) appear in Instagram during two timed captures, then reports what only appears in B.
 */
object Capture {
    const val WINDOW_MS = 30_000L

    var mode = 0 // 0 = idle, 1 = capturing A, 2 = capturing B
        private set
    var lastSnapshotAt = 0L

    private var endsAt = 0L
    private val a = HashSet<String>()
    private val b = HashSet<String>()

    fun start(m: Int) {
        mode = m
        endsAt = SystemClock.elapsedRealtime() + WINDOW_MS
        if (m == 1) a.clear() else b.clear()
    }

    fun active() = mode != 0 && SystemClock.elapsedRealtime() < endsAt

    fun leftMs(): Long = if (active()) endsAt - SystemClock.elapsedRealtime() else 0L

    fun record(items: Set<String>) {
        if (mode == 1) a.addAll(items) else if (mode == 2) b.addAll(items)
    }

    fun report(): String {
        if (a.isEmpty() || b.isEmpty()) {
            return "Need both captures first. A has ${a.size} items, B has ${b.size} items."
        }
        val diff = b.filter { it !in a }.sorted()
        return "A=${a.size} B=${b.size} only-in-B=${diff.size}\n" + diff.take(150).joinToString("\n")
    }
}
