package eu.darken.apl.common.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.DeviceDetective


@Suppress("ClassName")
sealed class Permission(
    val permissionId: String
) {
    open fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, permissionId) == PackageManager.PERMISSION_GRANTED
    }

    object POST_NOTIFICATIONS
        : Permission("android.permission.POST_NOTIFICATIONS"), RuntimePermission

    object ACCESS_COARSE_LOCATION
        : Permission("android.permission.ACCESS_COARSE_LOCATION"), RuntimePermission

    object ACCESS_FINE_LOCATION
        : Permission("android.permission.ACCESS_FINE_LOCATION"), RuntimePermission

    object CAMERA
        : Permission("android.permission.CAMERA"), RuntimePermission

    @SuppressLint("BatteryLife")
    object IGNORE_BATTERY_OPTIMIZATION
        : Permission("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"), Specialpermission {
        override fun isGranted(context: Context): Boolean {
            val pwm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pwm.isIgnoringBatteryOptimizations(BuildConfigWrap.APPLICATION_ID)
        }

        override fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.fromParts("package", context.packageName, null)
        }

        override fun createIntentFallback(context: Context): Intent = Intent().apply {
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        }
    }

}

interface RuntimePermission

interface Specialpermission {
    fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent
    fun createIntentFallback(context: Context): Intent? = null
}