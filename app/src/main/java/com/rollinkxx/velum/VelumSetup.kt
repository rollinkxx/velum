package com.rollinkxx.velum

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * Kesiapan agar Velum tidak dimatikan sistem — penentu apakah tunnel pulih sendiri.
 *
 * Tiga hal menentukan hidup-mati tunnel setelah OS membersihkan memori (khas ROM
 * agresif seperti Xiaomi/HyperOS, Oppo, Vivo):
 *  1. **Always-on VPN** — Android sendiri yang menyalakan ulang VPN setelah proses
 *     aplikasi dimatikan atau perangkat di-reboot;
 *  2. **Blokir koneksi tanpa VPN** — sekaligus berperan sebagai killswitch;
 *  3. **Bebas optimasi baterai** — sistem tidak membatasi proses latar belakang.
 *
 * Repo ini tidak bisa memaksakan ketiganya (memang bukan wewenang aplikasi), jadi
 * yang dilakukan hanya **menawarkan sekali** dengan bahasa awam lalu membuka layar
 * pengaturan sistem yang tepat. Keputusan tawaran dipisah ke [offer] yang murni
 * tanpa Android framework supaya bisa diuji unit.
 */
object VelumSetup {

    /** Apa yang masih perlu ditawarkan; [wanted] false berarti tidak ada tawaran. */
    data class Offer(val reason: String?, val reasons: List<String>) {
        val wanted: Boolean get() = reason != null && reasons.isNotEmpty()
    }

    /**
     * Menentukan tawaran — murni, teruji unit.
     *
     * Aturan yang disengaja:
     * - `null` (tidak diketahui, mis. izin dibatasi) **tidak** dianggap masalah:
     *   lebih baik tidak menawarkan apa pun daripada menuduh keliru pengguna;
     * - `postponed` menutup tawaran selamanya (sekali tawaran seumur pemasangan);
     * - urutan prioritas: selalu-aktif dulu (dampaknya paling besar), lalu baterai.
     */
    fun offer(
        registered: Boolean,
        alwaysOn: Boolean?,
        batteryUnrestricted: Boolean?,
        postponed: Boolean
    ): Offer {
        if (!registered || postponed) return Offer(null, emptyList())
        val reasons = ArrayList<String>(2)
        if (alwaysOn == false) reasons += REASON_ALWAYS_ON
        if (batteryUnrestricted == false) reasons += REASON_BATTERY
        val reason = when {
            alwaysOn == false && batteryUnrestricted == false -> "ready"
            alwaysOn == false -> REASON_ALWAYS_ON
            batteryUnrestricted == false -> REASON_BATTERY
            else -> null
        }
        return Offer(reason, reasons)
    }

    /**
     * Apakah always-on VPN sudah diarahkan ke Velum **dan** blokir-koneksi menyala.
     *
     * Dibaca langsung dari `Settings.Global` lewat refleksi nama kunci: dua kunci itu
     * ada di API 24+, tetapi baru muncul sebagai konstanta publik di API 26 — sedangkan
     * minSdk repo ini 24. Refleksi dipilih supaya tetap terkompilasi tanpa gerbang versi;
     * kegagalannya ditelan dan menghasilkan `null` (tidak diketahui) sesuai aturan [offer].
     */
    fun alwaysOnVpnReady(context: Context): Boolean? = try {
        val packageName = alwaysOnPackage(context) ?: return false
        packageName == context.packageName && alwaysOnLockdown(context) == true
    } catch (_: Exception) {
        null
    }

    /** Apakah Velum sudah dikecualikan dari optimasi baterai (null = tidak diketahui). */
    fun batteryUnrestricted(context: Context): Boolean? = try {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        null
    }

    fun vpnSettingsIntent(): Intent = Intent(ACTION_VPN_SETTINGS)

    /**
     * Halaman daftar aplikasi yang tidak dioptimalkan. Sengaja bukan
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (dialog langsung): aksi itu
     * memerlukan izin khusus yang dibatasi Play, sedangkan tujuannya sama saja —
     * pengguna tinggal memilih Velum dari daftar.
     */
    fun batterySettingsIntent(): Intent = Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION)

    private fun alwaysOnPackage(context: Context): String? =
        settingsString(context, "ALWAYS_ON_VPN_APP")

    private fun alwaysOnLockdown(context: Context): Boolean? =
        settingsBoolean(context, "ALWAYS_ON_VPN_LOCKDOWN")

    /** Membaca `Settings.Global.getString` untuk nama kunci yang baru publik di API 26. */
    private fun settingsString(context: Context, fieldName: String): String? = try {
        val field = Class.forName("android.provider.Settings\$Global").getField(fieldName)
        Settings.Global.getString(context.contentResolver, field.get(null) as String)
    } catch (_: Exception) {
        null
    }

    private fun settingsBoolean(context: Context, fieldName: String): Boolean? = try {
        val field = Class.forName("android.provider.Settings\$Global").getField(fieldName)
        Settings.Global.getInt(context.contentResolver, field.get(null) as String, 0) != 0
    } catch (_: Exception) {
        null
    }

    const val REASON_ALWAYS_ON = "alwaysOn"
    const val REASON_BATTERY = "battery"

    private const val ACTION_VPN_SETTINGS = "android.settings.VPN_SETTINGS"
    private const val ACTION_IGNORE_BATTERY_OPTIMIZATION =
        "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
}
