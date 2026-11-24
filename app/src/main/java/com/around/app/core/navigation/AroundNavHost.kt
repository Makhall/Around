package com.around.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.around.app.feature.circles.CircleDetailScreen
import com.around.app.feature.circles.CirclesScreen
import com.around.app.feature.circles.CreateCircleScreen
import com.around.app.feature.circles.JoinCircleScreen
import com.around.app.feature.history.HistoryScreen
import com.around.app.feature.map.CircleMapScreen
import com.around.app.feature.permissions.PermissionScreen
import com.around.app.feature.places.PlaceEditorScreen
import com.around.app.feature.places.PlacesScreen
import com.around.app.feature.profile.ProfileScreen
import com.around.app.feature.session.SessionScreen

@Composable
fun AroundNavHost(
    navController: NavHostController = rememberNavController(),
    notificationCircleId: String? = null,
    onNotificationCircleHandled: () -> Unit = {},
) {
    LaunchedEffect(notificationCircleId) {
        val circleId = notificationCircleId ?: return@LaunchedEffect
        val destination = navController.currentBackStackEntry?.destination
        if (destination == null ||
            destination.hasRoute<SessionRoute>() ||
            destination.hasRoute<ProfileSetupRoute>()
        ) {
            return@LaunchedEffect
        }
        navController.navigate(CircleMapRoute(circleId)) {
            launchSingleTop = true
        }
        onNotificationCircleHandled()
    }

    NavHost(
        navController = navController,
        startDestination = SessionRoute,
    ) {
        composable<SessionRoute> {
            SessionScreen(
                onReady = {
                    navController.navigate(CirclesRoute) {
                        popUpTo<SessionRoute> { inclusive = true }
                    }
                    notificationCircleId?.let { circleId ->
                        navController.navigate(CircleMapRoute(circleId))
                        onNotificationCircleHandled()
                    }
                },
                onNeedsProfile = {
                    navController.navigate(ProfileSetupRoute) {
                        popUpTo<SessionRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<ProfileSetupRoute> {
            ProfileScreen(
                isOnboarding = true,
                onSaved = {
                    navController.navigate(CirclesRoute) {
                        popUpTo<ProfileSetupRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<ProfileEditRoute> {
            ProfileScreen(
                isOnboarding = false,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable<CirclesRoute> {
            CirclesScreen(
                onCreateCircle = { navController.navigate(CreateCircleRoute) },
                onJoinCircle = { navController.navigate(JoinCircleRoute) },
                onOpenCircle = { circle ->
                    navController.navigate(CircleDetailRoute(circle.id))
                },
                onEditProfile = { navController.navigate(ProfileEditRoute) },
                onOpenPermissions = { navController.navigate(PermissionRoute) },
            )
        }

        composable<CreateCircleRoute> {
            CreateCircleScreen(
                onBack = { navController.popBackStack() },
                onOpenCircle = { circleId ->
                    navController.navigate(CircleDetailRoute(circleId)) {
                        popUpTo<CirclesRoute>()
                    }
                },
            )
        }

        composable<JoinCircleRoute> {
            JoinCircleScreen(
                onBack = { navController.popBackStack() },
                onJoined = { circleId ->
                    navController.navigate(CircleDetailRoute(circleId)) {
                        popUpTo<CirclesRoute>()
                    }
                },
            )
        }

        composable<CircleDetailRoute> {
            CircleDetailScreen(
                onBack = { navController.popBackStack() },
                onLeft = {
                    navController.popBackStack(CirclesRoute, inclusive = false)
                },
                onOpenMap = { circleId ->
                    navController.navigate(CircleMapRoute(circleId))
                },
                onOpenHistory = { circleId ->
                    navController.navigate(HistoryRoute(circleId))
                },
                onOpenPlaces = { circleId ->
                    navController.navigate(PlacesRoute(circleId))
                },
                onOpenPermissions = {
                    navController.navigate(PermissionRoute)
                },
            )
        }

        composable<CircleMapRoute> {
            CircleMapScreen(
                onBack = { navController.popBackStack() },
                onOpenHistory = { circleId ->
                    navController.navigate(HistoryRoute(circleId))
                },
                onOpenPermissions = {
                    navController.navigate(PermissionRoute)
                },
            )
        }

        composable<HistoryRoute> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<PlacesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlacesRoute>()
            PlacesScreen(
                onBack = { navController.popBackStack() },
                onAddPlace = {
                    navController.navigate(PlaceEditorRoute(route.circleId))
                },
                onEditPlace = { placeId ->
                    navController.navigate(
                        PlaceEditorRoute(route.circleId, placeId),
                    )
                },
            )
        }

        composable<PlaceEditorRoute> {
            PlaceEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable<PermissionRoute> {
            PermissionScreen(
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.popBackStack()
                },
            )
        }
    }
}
