package org.fossify.messages.activities

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context // Added import
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.util.Log // Added import
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.firebase.database.FirebaseDatabase // Added import (potential direct use)
import kotlinx.coroutines.CoroutineScope // Added import
import kotlinx.coroutines.Dispatchers // Added import
import kotlinx.coroutines.launch // Added import
import kotlinx.coroutines.withContext // Added import
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.navigationBarHeight
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.clearSystemDrafts
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.FirebaseSyncState // Added import
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult
import org.fossify.messages.ui.TransactionInfo
import org.fossify.messages.ui.TransactionActivity
import org.fossify.messages.ui.TransactionsInFBActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat // Added import
import java.util.Date // Added import
import java.util.Locale // Added import

import android.view.Menu
import android.view.MenuItem
import org.fossify.messages.utils.TransactionProcessor

class MainActivity : SimpleActivity() {
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null

    private val binding by viewBinding(ActivityMainBinding::inflate)
    private val mainActivityScope = CoroutineScope(Dispatchers.Main) // Scope for UI-related coroutines

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {

        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.mainCoordinator,
            nestedView = binding.conversationsList,
            useTransparentNavigation = true,
            useTopSearchMenu = true
        )

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }

        Log.d("MessagesApplication", "Application onCreate: Initializing FirebaseSyncState.")

        // FirebaseSyncState.initialize() is now called in MessagesApplication.kt
        FirebaseSyncState.initialize(
            { success, timestamp ->
                if (success) {
                    Log.i("MessagesApplication", "XXFirebaseSyncState initialized successfully from Application class, last synced timestamp: $timestamp")
                } else {
                    Log.e("MessagesApplication", "XXFirebaseSyncState failed to initialize from Application class.")
                }
            }
        )

        if (checkAppSideloading()) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        refreshMenuItems()

        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
        (binding.conversationsFab.layoutParams as? CoordinatorLayout.LayoutParams)?.bottomMargin =
            navigationBarHeight + resources.getDimension(org.fossify.commons.R.dimen.activity_margin)
                .toInt()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else {
            appLockManager.lock()
            super.onBackPressed()
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.getToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.toggleHideOnScroll(true)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            fadeOutSearch()
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            if (text.isNotEmpty()) {
                if (binding.searchHolder.alpha < 1f) {
                    binding.searchHolder.fadeIn()
                }
            } else {
                fadeOutSearch()
            }
            searchTextChanged(text)
        }

        binding.mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.show_recycle_bin -> launchRecycleBin()
                R.id.show_archived -> launchArchivedConversations()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                R.id.menu_transaction -> { 
                    val intent = Intent(this, TransactionActivity::class.java)
                    startActivity(intent)
                }
                R.id.action_show_fb_transactions -> { 
                    val intent = Intent(this, TransactionsInFBActivity::class.java)
                    startActivity(intent)
                }
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        binding.mainMenu.updateColors()
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()
        clearSystemDrafts()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
        // Call sync after messenger is initialized and conversations potentially loaded/updated
        mainActivityScope.launch { // Launch on main scope, syncNewSmsToFirebase will switch to IO
            syncNewSmsToFirebase(applicationContext)
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val allSystemConversations = getConversations(privateContacts = privateContacts) // Renamed for clarity

            allSystemConversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !allSystemConversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    allSystemConversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = allSystemConversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    insertOrUpdateConversation(conv)
                }
            }

            val currentConversationsForUI = conversationsDB.getNonArchived() as ArrayList<Conversation> // Use fresh list from DB
            runOnUiThread {
                setupConversations(currentConversationsForUI)
            }

            if (config.appRunCount == 1) {
                allSystemConversations.map { it.threadId }.forEach { threadId -> // Use allSystemConversations
                    val messages = getMessages(
                        threadId = threadId,
                        getImageResolutions = false,
                        includeScheduledMessages = false
                    )
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }


    fun convertToTimestamp(dateStr: String?): Long {
        val formatter = DateTimeFormatter.ofPattern("dd-MMM-yy")
        val localDate = if (dateStr != null) {
            LocalDate.parse(dateStr, formatter)
        } else {
            LocalDate.now()
        }
        return localDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    }

    // --- Start of Firebase SMS Sync Logic ---
    private suspend fun syncNewSmsToFirebase(context: Context) {
        withContext(Dispatchers.IO) { // Perform sync operations on a background thread
            try {
                FirebaseSyncState.awaitInitialization() // Ensure FirebaseSyncState is initialized
                val lastSyncedTimestamp = FirebaseSyncState.getLastMsgTimestamp()
                var newLatestTimestampToUpdate = lastSyncedTimestamp // In seconds
                Log.d("MainActivity", "Starting SMS sync. Last synced timestamp: $lastSyncedTimestamp")

                val allConversations = conversationsDB.getNonArchived()

                for (conversation in allConversations) {
//                    if (isIciciciSender(conversation.address)) {
                    if (isIciciciSender(conversation.phoneNumber)) {
                        // Fetch messages for this thread, from this sender, newer than lastSyncedTimestamp, ordered ASC by date
                        // Placeholder for actual DAO call:
                        // val messagesToProcess = messagesDB.getAscendingMessagesFromSenderInThreadNewerThan(
                        // conversation.threadId, "%-ICICIT-%", lastSyncedTimestamp
                        // )
                        // For now, filtering in code (less efficient for very large datasets):
//                        val messagesInThread = messagesDB.getMessagesFromThread(conversation.threadId, false)
                        val messagesInThread = messagesDB.getThreadMessages(conversation.threadId)
                        val messagesToProcess = messagesInThread
                            .filter {
                                // Check message specific sender if conversation.address is not granular enough
                                (it.senderName?.contains("-ICICIT-", ignoreCase = true) == true || conversation.phoneNumber?.contains("-ICICIT-", ignoreCase = true) == true) &&
//                                (it.address?.contains("-ICICIT-", ignoreCase = true) == true || conversation.address?.contains("-ICICIT-", ignoreCase = true) == true) &&
                                it.date > lastSyncedTimestamp
                            }
                            .sortedBy { it.date } // Process oldest of the new messages first

                        if (messagesToProcess.isNotEmpty()){
                             Log.d("MainActivity", "Found ${messagesToProcess.size} messages to process for thread ${conversation.threadId}")
                        }

                        for (message in messagesToProcess) {
                            Log.d("MainActivity", "Processing message ID ${message.id}, Date: ${message.date}")
                            // Ensure message.address is not null for TransactionProcessor
//                            val senderAddress = message.address ?: conversation.address ?: "UnknownSender"
//                            val transactionInfo = TransactionProcessor.processTransactionSms(
//                                context,
//                                message.body,
//                                senderAddress,
//                                message.date * 1000L // Assuming processor expects milliseconds
//                            )

                            val transactionInfo = TransactionProcessor.parseMessage(message.body)
//                            transactionInfo?.date = message.date.toLong()
                            transactionInfo?.date = convertToTimestamp(transactionInfo.strDateInMessage) //TODO find out the right member to retrieve the date/timestamp from the message.
                                 // TODO: it is after all available in the message conversation thread activity.

                            if (transactionInfo != null) {
                                TransactionProcessor.pushSingleTransactionNoCheck(transactionInfo, "J5");


//                                val datePath = formatDateForFirebasePath(message.date * 1000L) // Expects milliseconds
//                                val success = TransactionProcessor.addTransactionToFb(
//                                    context,
//                                    transactionInfo,
//                                    datePath
//                                )

//                                if (success) {
//                                    Log.i("MainActivity", "Successfully pushed message ID ${message.id} (key: ${transactionInfo.key}) to Firebase path: J5/sms_by_date/$datePath")
                                    newLatestTimestampToUpdate = message.date.toLong() // Update with the timestamp (in seconds) of the successfully pushed message
//                                } else {
//                                    Log.e("MainActivity", "Failed to push message ID ${message.id} to Firebase.")
//                                    // Optionally, break or implement retry logic
//                                }
                            } else {
                                 Log.w("MainActivity", "Failed to parse message ID ${message.id} into TransactionInfo.")
                            }
                        }
                    }
                }

                if (newLatestTimestampToUpdate > lastSyncedTimestamp) {
                    Log.i("MainActivity", "Updating Firebase sms_last_msg_timstamp to: $newLatestTimestampToUpdate")
                    FirebaseSyncState.updateLastMsgTimestampInFirebase(newLatestTimestampToUpdate)
                } else {
                    Log.d("MainActivity", "No new messages were synced. Timestamp remains $lastSyncedTimestamp")
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error during SMS sync to Firebase: ${e.message}", e)
            }
        }
    }

    private fun isIciciciSender(address: String?): Boolean {
        Log.d("MainActivity", "Checking if address is Icicici: $address")
        return address?.contains("-ICICIT-", ignoreCase = true) == true
    }

    private fun formatDateForFirebasePath(timestampMs: Long): String {
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH) // Using English for month consistency
        return sdf.format(Date(timestampMs))
    }
    // --- End of Firebase SMS Sync Logic ---


    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        val sortedConversations = conversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>

        if (cached && config.appRunCount == 1) {
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        initMessenger() // This will also trigger the sync logic
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, R.id.menu_transaction, Menu.NONE, "Transactions")
            .setIcon(R.drawable.ic_transaction)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_transaction -> {
                startActivity(Intent(this, TransactionActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
