package com.around.app.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SessionRoute

@Serializable
data object ProfileSetupRoute

@Serializable
data object ProfileEditRoute

@Serializable
data object CirclesRoute

@Serializable
data object CreateCircleRoute

@Serializable
data object JoinCircleRoute

@Serializable
data class CircleDetailRoute(val circleId: String)

@Serializable
data class CircleMapRoute(val circleId: String)

@Serializable
data object PermissionRoute
