package com.example.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class BlockerService : AccessibilityService() {

    private var lastBlockAt = 0L
    private var consecutive = 0

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
            "com.instagram.android" -> instagramIds
            else -> return
        }
        if (Prefs.isPaused(this)) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        if (isVideoPlayerShowing(root, pkg, ids)) block()
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

    override fun onInterrupt() {}
}
