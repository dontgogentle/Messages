package org.fossify.messages.models

sealed interface RoomDisplayItem {
    data class RoomHeader(val roomNumber: String) : RoomDisplayItem
}
