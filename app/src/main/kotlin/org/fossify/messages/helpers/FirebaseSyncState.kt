package org.fossify.messages.helpers

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FirebaseSyncState {

    private const val TAG = "FirebaseSyncState"
    private const val FB_GLOBALS_PATH = "J5/Globals"
    private const val FB_SMS_LAST_MSG_TIMESTAMP_KEY = "sms_last_msg_timstamp" // Typo from user request

    private val database = FirebaseDatabase.getInstance().reference
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var smsLastMsgTimestamp: Long = 0L
    @Volatile
    private var initialized: Boolean = false
    @Volatile
    private var initializing: Boolean = false

    fun initialize(callback: ((success: Boolean, timestamp: Long) -> Unit)? = null) {
        if (initialized || initializing) {
            callback?.invoke(initialized, smsLastMsgTimestamp)
            return
        }
        initializing = true
        Log.d(TAG, "Initializing smsLastMsgTimestamp from Firebase...")

        database.child(FB_GLOBALS_PATH).child(FB_SMS_LAST_MSG_TIMESTAMP_KEY)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                    smsLastMsgTimestamp = timestamp
                    initialized = true
                    initializing = false
                    Log.d(TAG, "Initialized smsLastMsgTimestamp: $smsLastMsgTimestamp")
                    callback?.invoke(true, smsLastMsgTimestamp)
                }

                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
                    Log.e(TAG, "Error initializing smsLastMsgTimestamp: ${error.message}")
                    smsLastMsgTimestamp = 0L // Default to 0 on error
                    initialized = false // Consider if an error means it's not truly initialized
                    initializing = false
                    callback?.invoke(false, smsLastMsgTimestamp)
                }
            })
    }

    fun getLastMsgTimestamp(): Long {
        if (!initialized && !initializing) {
            Log.w(TAG, "Accessed getLastMsgTimestamp before initialization. Consider calling initialize() first.")
        }
        return smsLastMsgTimestamp
    }

    fun updateLastMsgTimestampInFirebase(newTimestamp: Long) {
        if (!initialized && !initializing) {
            // If not initialized, it means we don't know the baseline from FB.
            // Updating FB without this baseline could lead to inconsistencies.
            // It's safer to ensure initialization happens first.
            Log.e(TAG, "Attempted to update timestamp in Firebase before initialization. Aborting.")
            return
        }

        Log.d(TAG, "Updating smsLastMsgTimestamp to: $newTimestamp and writing to Firebase.")
        smsLastMsgTimestamp = newTimestamp // Update local cache immediately

        scope.launch {
            database.child(FB_GLOBALS_PATH).child(FB_SMS_LAST_MSG_TIMESTAMP_KEY)
                .setValue(newTimestamp)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully updated timestamp in Firebase to $newTimestamp")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update timestamp in Firebase: ${e.message}")
                    // Optional: Consider reverting local smsLastMsgTimestamp or scheduling a retry
                }
        }
    }

    // Call this if you want to ensure it's initialized before proceeding using coroutines
    suspend fun awaitInitialization(): Long {
        if (initialized) return smsLastMsgTimestamp
        if (initializing) {
            // A simple way to wait for ongoing initialization if called from a coroutine.
            // More robust solutions might involve a Deferred or a dedicated StateFlow.
            return suspendCancellableCoroutine { continuation ->
                val checkIntervalMillis = 100L
                val maxWaitMillis = 5000L // 5 seconds timeout
                var waitedMillis = 0L

                scope.launch {
                    while (initializing && waitedMillis < maxWaitMillis) {
                        kotlinx.coroutines.delay(checkIntervalMillis)
                        waitedMillis += checkIntervalMillis
                    }
                    if (initialized) {
                        continuation.resume(smsLastMsgTimestamp)
                    } else {
                        Log.e(TAG, "Timeout or error waiting for initialization in awaitInitialization.")
                        continuation.resume(smsLastMsgTimestamp) // return current (likely 0) or throw
                    }
                }
            }
        }
        
        // If not initialized and not initializing, start initialization and wait
        return suspendCancellableCoroutine { continuation ->
            initialize { success, timestamp ->
                continuation.resume(timestamp)
            }
        }
    }
}
