package org.fossify.messages.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.messages.extensions.config // Added import for config
import org.fossify.messages.helpers.Config // Added import for Config constants
import org.fossify.messages.ui.TransactionActivity // Added import for TransactionActivity
import org.fossify.messages.ui.TransactionsInFBActivity // Added import for TransactionsInFBActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        val intent = when (config.defaultActivity) {
            Config.DEFAULT_ACTIVITY_TRANSACTIONS -> Intent(this, TransactionActivity::class.java)
            Config.DEFAULT_ACTIVITY_TRANSACTIONS_FB -> Intent(this, TransactionsInFBActivity::class.java)
            else -> Intent(this, MainActivity::class.java) // Default to MainActivity (Conversations)
        }
        startActivity(intent)
        finish()
    }
}
