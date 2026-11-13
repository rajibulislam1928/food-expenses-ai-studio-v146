package com.example.notifications

object AppVisibilityTracker {
  @Volatile
  var isForeground: Boolean = false
    private set

  fun onAppStarted() {
    isForeground = true
  }

  fun onAppStopped() {
    isForeground = false
  }
}
