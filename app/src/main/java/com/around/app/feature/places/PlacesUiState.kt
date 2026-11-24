package com.around.app.feature.places

import com.around.app.data.model.Circle
import com.around.app.data.model.Place
import com.around.app.data.model.PlaceError

data class PlacesUiState(
    val loading: Boolean = true,
    val circle: Circle? = null,
    val places: List<Place> = emptyList(),
    val currentUid: String? = null,
    val deletingId: String? = null,
    val error: PlaceError? = null,
)
