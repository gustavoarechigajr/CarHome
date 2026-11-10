package dev.gus.carhome

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CarHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NightSync.start(this)
        TwSource.start(this)
        BtLog.listen(this)
        BtLog.start(this)
    }
}

/** Wakes the app process at boot so headlight sync runs even before the home screen shows. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        NightSync.start(context)   // starting the process already runs CarHomeApp; this is idempotent
    }
}
