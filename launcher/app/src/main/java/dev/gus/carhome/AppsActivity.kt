package dev.gus.carhome

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** Simple app drawer: every launchable app, alphabetical. Long-press = app info. */
class AppsActivity : Activity() {

    private data class Entry(val label: String, val component: ComponentName, val icon: Drawable)

    private var entries: List<Entry> = emptyList()
    private lateinit var adapter: BaseAdapter
    private var labelColor = 0
    private var labelFont: android.graphics.Typeface = android.graphics.Typeface.DEFAULT

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        ThemePrefs.override(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        // Same background and back-button look as the home screen's active theme
        val pal = ThemePrefs.theme(this).palette(ThemePrefs.isNight(this))
        (findViewById<ViewGroup>(android.R.id.content).getChildAt(0)).background =
            dev.gus.carhome.theme.ThemeKit.linear(pal.bgStops)
        window.statusBarColor = pal.bgStops[0]
        findViewById<android.widget.ImageButton>(R.id.back).apply {
            background = dev.gus.carhome.theme.ThemeKit.pressable(
                dev.gus.carhome.theme.ThemeKit.linear(pal.chipStops, dev.gus.carhome.theme.ThemeKit.dp(context, 28f)), pal.ink)
            imageTintList = android.content.res.ColorStateList.valueOf(pal.ink)
        }

        val theme = ThemePrefs.theme(this)
        labelColor = pal.ink
        labelFont = dev.gus.carhome.theme.ThemeKit.font(this, theme.fontAsset, 100, 500)
        val grid = findViewById<GridView>(R.id.grid)
        adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(i: Int) = entries[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
                val e = entries[i]
                v.findViewById<ImageView>(R.id.icon).setImageDrawable(e.icon)
                v.findViewById<TextView>(R.id.label).apply {
                    text = e.label
                    setTextColor(labelColor)
                    typeface = labelFont
                }
                v.contentDescription = e.label
                return v
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, i, _ ->
            val e = entries[i]
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        grid.setOnItemLongClickListener { _, _, i, _ ->
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", entries[i].component.packageName, null)))
            true
        }
        load()
    }

    private fun load() {
        Thread {
            val pm = packageManager
            val list = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    Entry(it.loadLabel(pm).toString(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        it.loadIcon(pm))
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { entries = list; adapter.notifyDataSetChanged() }
        }.start()
    }
}
