package de.datlag.openfe.commons

import android.content.pm.PackageManager
import android.os.Build

@Suppress("DEPRECATION")
fun PackageManager.isTelevision(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.hasSystemFeature(PackageManager.FEATURE_TELEVISION) || this.hasSystemFeature(
                PackageManager.FEATURE_LEANBACK) || this.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
        } else {
            this.hasSystemFeature(PackageManager.FEATURE_TELEVISION) || this.hasSystemFeature(
                PackageManager.FEATURE_LEANBACK)
        }
    } else {
        this.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
}