package org.fossify.messages.activities

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.fossify.messages.adapters.InmateAdapter
import org.fossify.messages.databinding.ActivitySiteRoomDetailsBinding
import org.fossify.messages.helpers.Config
import org.fossify.messages.models.Inmate
import org.fossify.messages.models.RoomDisplayItem
import org.fossify.messages.models.RoomHeader
import java.util.Locale

class SiteRoomDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySiteRoomDetailsBinding
    private lateinit var inmateAdapter: InmateAdapter
    private val allDisplayItems = mutableListOf<RoomDisplayItem>()
    private val filteredDisplayItems = mutableListOf<RoomDisplayItem>()

    private lateinit var currentSite: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySiteRoomDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentSite = Config(this).selectedSiteName

        setupToolbar()
        setupSiteAbbreviation()
        setupRecyclerView()
        setupSearch()

        fetchInmates()
    }

    private fun setupToolbar() {
        // No title to be set on the toolbar itself
    }

    private fun getSiteAbbreviation(siteName: String): String {
        if (siteName.length <= 2) {
            return siteName.uppercase(Locale.getDefault())
        }
        val words = siteName.split(Regex("""(?=[A-Z])|\s+|_|-""")) // Split by uppercase letters or common delimiters
        var abbreviation = ""
        for (word in words) {
            if (word.isNotEmpty()) {
                abbreviation += word[0]
            }
            if (abbreviation.length >= 2) break
        }
        return abbreviation.uppercase(Locale.getDefault()).take(2)
    }

    private fun setupSiteAbbreviation() {
        val abbreviation = getSiteAbbreviation(currentSite)
        binding.siteAbbreviationTextView.text = abbreviation

        // Dynamic background and text color for contrast
        // Using a fixed background color for simplicity, e.g., theme's primary color
        // You might want to fetch this from your theme (R.attr.colorPrimary)
        val backgroundColor = Color.parseColor("#FF6200EE") // Example: Purple, replace with your theme color
        binding.siteAbbreviationTextView.setBackgroundColor(backgroundColor)

        // Set text color for contrast
        val textColor = if (ColorUtils.calculateLuminance(backgroundColor) < 0.5) {
            Color.WHITE // Dark background, light text
        } else {
            Color.BLACK // Light background, dark text
        }
        binding.siteAbbreviationTextView.setTextColor(textColor)
    }


    private fun setupRecyclerView() {
        inmateAdapter = InmateAdapter()
        binding.inmatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SiteRoomDetailsActivity)
            adapter = inmateAdapter
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterInmates(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchInmates() {
        // Ensure toolbar title is set - REMOVED as title is removed from toolbar

        val databasePath = "$currentSite/AccIndexes/active" // Fetch all rooms
        val database = FirebaseDatabase.getInstance().getReference(databasePath)

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allDisplayItems.clear()
                for (roomSnapshot in snapshot.children) { // Iterates through each room (e.g., A1, A2)
                    val roomKey = roomSnapshot.key
                    if (roomKey != null) {
                        allDisplayItems.add(RoomHeader(roomKey))
                        // Children of roomSnapshot are directly the inmates (userKey -> userInfo)
                        for (inmateSnapshot in roomSnapshot.children) { // Iterates through each inmate under the current room
                            val inmate = inmateSnapshot.getValue(Inmate::class.java)
                            inmate?.let {
                                val inmateWithId = it.copy(id = inmateSnapshot.key ?: "") // The key of inmateSnapshot is the user ID
                                allDisplayItems.add(inmateWithId)
                            }
                        }
                    }
                }
                filterInmates(binding.searchEditText.text.toString()) // Apply current filter
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                // Log.e("SiteRoomDetailsActivity", "Firebase error: ${error.message}")
            }
        })
    }

    private fun findPrecedingHeaderForItem(inmate: Inmate, list: List<RoomDisplayItem>): RoomHeader? {
        val inmateIndex = list.indexOf(inmate)
        if (inmateIndex == -1) return null
        for (i in inmateIndex downTo 0) {
            if (list[i] is RoomHeader) {
                return list[i] as RoomHeader
            }
        }
        return null
    }

    private fun filterInmates(query: String) {
        filteredDisplayItems.clear()
        if (query.isEmpty()) {
            filteredDisplayItems.addAll(allDisplayItems)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            val headersToAdd = mutableSetOf<RoomHeader>() // Keep track of headers to add
            val itemsToAdd = mutableListOf<RoomDisplayItem>() // All items that match or whose headers match

            // First pass: identify all matching inmates and their headers
            for (item in allDisplayItems) {
                if (item is Inmate && item.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    itemsToAdd.add(item)
                    findPrecedingHeaderForItem(item, allDisplayItems)?.let { headersToAdd.add(it) }
                }
            }

            // Second pass: identify all matching headers
            for (item in allDisplayItems) {
                if (item is RoomHeader && item.roomNumber.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    headersToAdd.add(item)
                }
            }

            // Build the filtered list: add headers first, then inmates under their respective headers
            for (header in allDisplayItems.filterIsInstance<RoomHeader>()) {
                if (headersToAdd.contains(header)) {
                    if (!filteredDisplayItems.contains(header)) {
                        filteredDisplayItems.add(header)
                    }
                    // Add inmates that belong to this header and are in itemsToAdd
                    val headerIndexInAll = allDisplayItems.indexOf(header)
                    for (i in headerIndexInAll + 1 until allDisplayItems.size) {
                        val potentialInmate = allDisplayItems[i]
                        if (potentialInmate is RoomHeader) break // Next header
                        if (potentialInmate in itemsToAdd && !filteredDisplayItems.contains(potentialInmate)) {
                            filteredDisplayItems.add(potentialInmate)
                        }
                    }
                }
            }
            // Add any matching inmates whose headers didn't match directly but were added because of the inmate
            for (item in itemsToAdd) {
                if (!filteredDisplayItems.contains(item)) {
                    // This case might be tricky if an inmate matches but its header doesn't.
                    // The current logic ensures header is added if its inmate matches.
                    // This explicit add might be redundant or could be refined based on exact desired behavior for orphaned items.
                    // For now, if an inmate is in itemsToAdd, its header is in headersToAdd, and both should be added by the loop above.
                }
            }
        }
        inmateAdapter.submitList(filteredDisplayItems.toList())
    }

    // Optional: Handle back navigation if you enabled it in the toolbar
    // override fun onSupportNavigateUp(): Boolean {
    //     onBackPressedDispatcher.onBackPressed()
    //     return true
    // }
}
