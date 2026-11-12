package com.thejusticeman.glyphos

/** Holds the stable icon layout while transient gesture obstacles are applied. */
internal class GestureGhostLayoutSnapshot {
  private var originalNodes: List<LauncherIconNode>? = null

  // Keep the pre-gesture layout as the solver seed until the no-ghost layout has settled.
  fun rememberIfAbsent(nodes: List<LauncherIconNode>) {
    if (originalNodes == null) {
      originalNodes = nodes.toList()
    }
  }

  fun previousNodes(currentNodes: List<LauncherIconNode>): List<LauncherIconNode> {
    return originalNodes ?: currentNodes
  }

  fun onLayoutSettled(gestureGhostActive: Boolean, layoutSettled: Boolean) {
    if (!gestureGhostActive && layoutSettled) {
      originalNodes = null
    }
  }
}
