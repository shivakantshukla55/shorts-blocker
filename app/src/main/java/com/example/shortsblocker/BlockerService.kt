package com.example.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class BlockerService : AccessibilityService() {

    private var lastBlockAt = 0L
    private var consecutive = 0

    private val igPkg = "com.instagram.android"

    // View ids that only exist while the full-screen vertical video player is on screen.
    // App updates can rename these; if blocking stops working, update the lists below.
    private val youtubeIds = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_fragment_root",
        "shorts_player_container"
    )
    private val instagramIds = listOf(
        "clips_viewer_view_pager",
        "clips_video_container",
        "root_clips_layout"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val ids = when (pkg) {
            "com.google.android.youtube" -> youtubeIds
            igPkg -> instagramIds
            else -> return
        }
        // Diagnostic capture: while recording, only observe Instagram and never block.
        if (pkg == igPkg && Capture.active()) {
            val now = SystemClock.elapsedRealtime()
            if (now - Capture.lastSnapshotAt >= 400) {
                Capture.lastSnapshotAt = now
                Capture.record(snapshot())
            }
            return
        }

        if (Prefs.isPaused(this)) return

        // The press-and-hold preview opens as its own pop-up window, so check every window.
        if (pkg == igPkg && isPeekShowing()) {
            block()
            return
        }

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        if (isVideoPlayerShowing(root, pkg, ids)) block()
    }

    /** True while Instagram's long-press preview ("peek") is on screen in any window. */
    private fun isPeekShowing(): Boolean {
        val roots = ArrayList<AccessibilityNodeInfo>()
        windows?.forEach { w -> w.root?.let { roots.add(it) } }
        rootInActiveWindow?.let { roots.add(it) }
        for (r in roots) {
            if (r.packageName?.toString() != igPkg) continue
            val nodes = r.findAccessibilityNodeInfosByViewId("$igPkg:id/peek_container")
            if (nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    private fun isVideoPlayerShowing(root: AccessibilityNodeInfo, pkg: String, ids: List<String>): Boolean {
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            if (nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    private fun block() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 700) return
        consecutive = if (now - lastBlockAt < 4000) consecutive + 1 else 1
        lastBlockAt = now

        // Back usually leaves the Shorts/Reels screen. If it keeps coming back, go Home.
        if (consecutive >= 3) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            consecutive = 0
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        Toast.makeText(this, "Shorts / Reels blocked", Toast.LENGTH_SHORT).show()
    }

    /** Collects "class|viewId|visible" for every Instagram view on screen, across all windows. */
    private fun snapshot(): Set<String> {
        val out = HashSet<String>()
        val allWindows = windows
        if (allWindows != null) {
            for (w in allWindows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() != igPkg) continue
                out.add("WINDOW type=${w.type} layer=${w.layer}")
                walk(r, 0, out)
            }
        }
        if (out.isEmpty()) {
            rootInActiveWindow?.let { walk(it, 0, out) }
        }
        return out
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableSet<String>) {
        if (depth > 45) return
        val id = node.viewIdResourceName?.substringAfter(":id/") ?: "-"
        out.add("${node.className}|$id|visible=${node.isVisibleToUser}")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, out)
        }
    }

    override fun onInterrupt() {}
}