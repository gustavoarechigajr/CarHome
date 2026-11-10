package dev.gus.carhome

import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Package names on this Topway/Dofun head unit. */
object Apps {
    const val ZLINK = "com.zjinnova.zlink"
    const val ZLINK_MAIN = "com.zjinnova.android.zlink.features.main.MainActivity"
    const val MUSIC = "com.tw.music"
    const val PHONE = "com.tw.bt"
    const val CAR_SETTINGS = "com.dofun.carsetting"
}

fun Context.launchPackage(pkg: String, fallbackClass: String? = null): Boolean {
    val intent = packageManager.getLaunchIntentForPackage(pkg)
        ?: fallbackClass?.let { Intent(Intent.ACTION_MAIN).setClassName(pkg, it) }
    if (intent == null) {
        Toast.makeText(this, "App not installed: $pkg", Toast.LENGTH_SHORT).show()
        return false
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        Toast.makeText(this, "Can't open $pkg", Toast.LENGTH_SHORT).show()
        false
    }
}
