package app.romsafe.android.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 blocks connections to the home network for apps targeting it unless they hold
 * ACCESS_LOCAL_NETWORK (shown as "Nearby devices"). The server is almost always on that network,
 * and a blocked connection just times out, so the app asks before pairing and explains failures.
 */
object LocalNetwork {
    // A plain string, inlined at compile time; only ever requested on Android 17 and later.
    @SuppressLint("InlinedApi")
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    /** True when this Android version gates the home network and the app doesn't have it yet. */
    fun needsGrant(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED

    const val DENIED_MESSAGE =
        "RomSafe needs the Nearby devices permission to reach a server on your network. " +
            "Allow it in Settings → Apps → RomSafe → Permissions, then try again."
}
