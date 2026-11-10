package dev.gus.carhome

import android.service.notification.NotificationListenerService

/**
 * Empty on purpose. Android only lets an app read other apps' media sessions
 * (for the Now playing card) if it has an enabled notification listener.
 * Grant once with:  adb shell cmd notification allow_listener dev.gus.carhome/.MediaListener
 */
class MediaListener : NotificationListenerService()
