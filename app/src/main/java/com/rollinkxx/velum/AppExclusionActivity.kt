package com.rollinkxx.velum

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

/**
 * Memilih aplikasi yang **dikecualikan** dari tunnel (split tunneling): aplikasi
 * yang dicentang memakai jalur internet langsung, sisanya tetap lewat Velum.
 *
 * Sengaja tanpa RecyclerView/daftar berkinerja tinggi: layar ini dibuka jarang
 * dan daftarnya pendek (hanya aplikasi yang bisa diluncurkan), sehingga
 * LinearLayout seadanya menjaga ukuran APK tetap kecil.
 *
 * Yang bisa dibaca daftarnya dibatasi oleh `<queries>` di manifest (aplikasi
 * peluncur), sehingga tidak perlu izin `QUERY_ALL_PACKAGES` yang dibatasi.
 */
class AppExclusionActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private val boxes = LinkedHashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_exclusion)
        VelumInsets.applySystemBars(findViewById(R.id.root))
        prefs = Prefs.of(this)
        list = findViewById(R.id.appList)

        val excluded = prefs.excludedApps
        val warna = resources.getColor(R.color.fg, theme)
        val apps = launcherApps()
        if (apps.isEmpty()) {
            // Perangkat tanpa aplikasi peluncur yang terbaca (mis. profil kerja
            // terbatas): jelaskan keadaan kosong, jangan biarkan layar menggantung.
            list.addView(TextView(this).apply {
                setText(R.string.excluded_empty)
                setTextColor(resources.getColor(R.color.muted, theme))
                textSize = 13f
                setPadding(0, dp(12), 0, dp(12))
            })
        }
        for (app in apps) {
            val box = CheckBox(this).apply {
                text = app.label
                isChecked = app.packageName in excluded
                setTextColor(warna)
                setPadding(0, dp(12), 0, dp(12))
            }
            boxes[app.packageName] = box
            list.addView(box)
        }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        // Tombol kembali di bilah atas: memakai dispatcher yang sama dengan gestur
        // sistem, sehingga ikut tampil mulus pada animasi predictive back.
        findViewById<ImageButton>(R.id.back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * dp -> piksel.
     *
     * `View.setPadding` menerima **piksel**, dan `12` mentah berarti 12px: di layar 3x
     * itu hanya 4dp, sehingga jarak antarbaris nyaris hilang di ponsel padat piksel
     * sementara tetap longgar di ponsel lama. Ukuran visual tidak boleh bergantung pada
     * kerapatan layar secara kebetulan seperti itu.
     */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun save() {
        prefs.excludedApps = boxes.filter { it.value.isChecked }.keys.toSet()
        Toast.makeText(this, R.string.excluded_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Aplikasi peluncur terurut; Velum sendiri tidak ditawarkan. */
    private fun launcherApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        return resolved
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private data class AppInfo(val packageName: String, val label: String)
}
