package org.fossify.messages

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import org.fossify.messages.BuildConfig // Import BuildConfig

class MessagesApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Explicitly initialize FirebaseApp
        try {
            FirebaseApp.initializeApp(this)
            Log.d("MessagesApplication", "FirebaseApp initialized explicitly.")
        } catch (e: Exception) {
            Log.e("MessagesApplication", "Error initializing FirebaseApp: ${e.message}", e)
            // It's crucial to see if this catch block is hit
        }

        if (BuildConfig.DEBUG) {
            try {
                val firebaseDatabase = FirebaseDatabase.getInstance()
                // Ensure this IP is correct for your setup.
                // Your React Native app uses 100.120.198.49 from the Android Emulator.
                firebaseDatabase.useEmulator("100.120.198.49", 9000)
                Log.d("MessagesApplication", "Firebase Database Emulator configured for 100.120.198.49:9000")
            } catch (e: Exception) {
                Log.e("MessagesApplication", "Error configuring Firebase Database Emulator: ${e.message}", e)
            }
        } else {
            Log.d("MessagesApplication", "Release build, not using Firebase Database emulator.")
        }
    }
}
