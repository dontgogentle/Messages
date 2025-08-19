package org.fossify.messages.extensions

import android.content.Context
import android.provider.Telephony
import android.os.Build
import android.util.Log

fun Context.isDefaultSmsApp(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        packageName == Telephony.Sms.getDefaultSmsPackage(this)
        android.util.Log.w("SMS", "package is default app package Name:"+packageName)
        android.util.Log.w("SMS", "package Telephony.Sms.getDefaultSmsPackage:"+Telephony.Sms.getDefaultSmsPackage(this))
        return true
    } else {
        // For versions prior to KitKat, determining the default SMS app is more complex.
        // If your app's minSdkVersion is 19 (KitKat) or higher, this 'else' branch
        // will not be executed. For simplicity and modern app development,
        // this example assumes minSdk >= 19.
        // If supporting pre-KitKat is essential, a different approach would be needed here.
        return false // Or handle appropriately for pre-KitKat.
    }
}
