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
        if (Prefs.isPaused(this)) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        if (isVideoPlayerShowing(root, pkg, ids)) {
            block()
        } else if (pkg == igPkg && isExplorePreviewShowing(root)) {
            block()
        }
    }

    private fun isVideoPlayerShowing(root: AccessibilityNodeInfo, pkg: String, ids: List<String>): Boolean {
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            if (nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    /**
     * The press-and-hold preview on the Instagram Explore grid plays a video, while the
     * grid itself only shows still thumbnails. So: Explore tab selected + a visible video
     * surface on screen = a reel preview is playing.
     */
    private fun isExplorePreviewShowing(root: AccessibilityNodeInfo): Boolean {
        if (!isExploreTabSelected(root)) return false
        return hasVisibleVideoSurface(root, 0)
    }

    private fun isExploreTabSelected(root: AccessibilityNodeInfo): Boolean {
        val byId = root.findAccessibilityNodeInfosByViewId("$igPkg:id/search_tab")
        if (byId.any { it.isSelected }) return true
        // findAccessibilityNodeInfosByText also matches content descriptions, ignoring case.
        return root.findAccessibilityNodeInfosByText("Search and explore").any { it.isSelected }
    }

    private fun hasVisibleVideoSurface(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 40) return false
        val cls = node.className?.toString() ?: ""
        if (node.isVisibleToUser && (cls.contains("TextureView") || cls.contains("SurfaceView"))) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasVisibleVideoSurface(child, depth + 1)) return true
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
