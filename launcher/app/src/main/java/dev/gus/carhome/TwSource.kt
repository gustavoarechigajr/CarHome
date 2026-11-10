package dev.gus.carhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log

/**
 * The unit's current audio source, straight from the Topway system (no guessing from logs).
 *   8 = Bluetooth music, 3 = car music app, 1 = radio, 0 = none/Android media, -1 = unknown.
 *
 * Primary: bind com.tw.service.xt CommandService (exported, no permission), register a callback (tx 1);
 * it pushes Bundle{action:"Source_recieve", SourceValue:int} on every change (callback tx 8).
 * Ask for the current value with extendedInterface (tx 67) {project:"system", action:"source_request"}.
 * Backup: IActivityTaskManager.getTWSource() = raw transaction 166 on "activity_task".
 * (Parcel layouts from the vendor dex; see research/vendor and REVIEW.md.)
 */
object TwSource {
    const val BLUETOOTH = 8
    private const val SVC = "com.tw.service.xt.aidl.ITWCommandAidl"
    private const val CB = "com.tw.service.xt.aidl.ITWCommandCallbackAidl"
    private const val TX_REGISTER = 1
    private const val TX_UNREGISTER = 2
    private const val TX_EXTENDED = 67
    private const val CB_EXTENDED = 8

    @Volatile var current = -1
        private set
    /** Called on the main thread with (newSource, previousSource). */
    var onChange: ((Int, Int) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var remote: IBinder? = null
    private var appCtx: Context? = null

    /** Visible for tests: feed a source value as if the system reported it. */
    fun report(source: Int) {
        if (source == current) return
        val old = current
        current = source
        Log.i("CarHome", "audio source $old -> $source")
        onChange?.invoke(source, old)
    }

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(CB); return true }
            if (code !in 1..8) return super.onTransact(code, data, reply, flags)
            try {
                data.enforceInterface(CB)
                when (code) {
                    CB_EXTENDED -> {
                        val b = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                        if (b?.getString("action") == "Source_recieve") {   // (sic) firmware spelling
                            val s = b.getInt("SourceValue", -1)
                            main.post { report(s) }
                        }
                    }
                    4 -> { data.readInt(); data.readString(); data.readString() }
                    else -> data.readInt()
                }
            } catch (e: Exception) {
                Log.w("CarHome", "TwSource callback: ${e.message}")
            }
            reply?.writeNoException()
            return true   // xt waits for us: return fast
        }
    }

    private fun call(b: IBinder, code: Int, write: (Parcel) -> Unit) {
        val d = Parcel.obtain(); val r = Parcel.obtain()
        try {
            d.writeInterfaceToken(SVC); write(d)
            b.transact(code, d, r, 0)
            r.readException()
        } finally { r.recycle(); d.recycle() }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            if (b == null) return
            remote = b
            try {
                call(b, TX_REGISTER) { it.writeStrongBinder(callback) }
                request()
            } catch (e: Exception) { Log.w("CarHome", "TwSource register failed: ${e.message}") }
        }
        override fun onServiceDisconnected(n: ComponentName?) { remote = null }   // rebinds; re-registers above
    }

    fun start(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        try {
            appCtx!!.bindService(Intent("com.tw.service.xt.CommandService.Bind").setPackage("com.tw.service.xt"),
                conn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) { Log.w("CarHome", "TwSource bind failed: ${e.message}") }
        // If the callback hasn't answered shortly, ask the framework directly
        main.postDelayed({ if (current < 0) queryFramework()?.let { report(it) } }, 800)
    }

    /** Ask xt to push the current source to our callback (async, ~50 ms). Cheap; call on resume. */
    fun request() {
        val b = remote ?: run { queryFramework()?.let { report(it) }; return }
        try {
            call(b, TX_EXTENDED) { p ->
                p.writeInt(1)
                Bundle().apply { putString("project", "system"); putString("action", "source_request") }.writeToParcel(p, 0)
            }
        } catch (e: Exception) {
            Log.w("CarHome", "TwSource request failed: ${e.message}")
            queryFramework()?.let { report(it) }
        }
    }

    /** Backup: IActivityTaskManager.getTWSource() (tx 166) via ServiceManager, which is greylisted on Q. */
    fun queryFramework(): Int? = try {
        val b = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "activity_task") as IBinder
        val d = Parcel.obtain(); val r = Parcel.obtain()
        try {
            d.writeInterfaceToken("android.app.IActivityTaskManager")
            b.transact(166, d, r, 0)
            r.readException()
            r.readInt()
        } finally { r.recycle(); d.recycle() }
    } catch (e: Exception) {
        Log.w("CarHome", "getTWSource failed: ${e.message}")
        null
    }

    fun stop() {
        val ctx = appCtx ?: return
        remote?.let { b -> try { call(b, TX_UNREGISTER) { it.writeStrongBinder(callback) } } catch (_: Exception) {} }
        try { ctx.unbindService(conn) } catch (_: Exception) {}
        appCtx = null; remote = null
    }

    /** Test-only. */
    fun resetForTest() { current = -1; onChange = null }
}
