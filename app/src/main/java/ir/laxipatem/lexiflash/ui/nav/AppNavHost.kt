package ir.laxipatem.lexiflash.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.data.repo.UserSettings
import ir.laxipatem.lexiflash.ui.about.AboutScreen
import ir.laxipatem.lexiflash.ui.home.HomeScreen
import ir.laxipatem.lexiflash.ui.importer.ImportScreen
import ir.laxipatem.lexiflash.ui.progress.ProgressScreen
import ir.laxipatem.lexiflash.ui.quiz.QuizResultScreen
import ir.laxipatem.lexiflash.ui.quiz.QuizScreen
import ir.laxipatem.lexiflash.ui.review.ReviewScreen
import ir.laxipatem.lexiflash.ui.settings.SettingsScreen
import ir.laxipatem.lexiflash.ui.study.StudyScreen

/** One entry in the bottom navigation bar. */
private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomDestination(Routes.REVIEW, R.string.nav_review, Icons.Filled.Refresh, Icons.Outlined.Refresh),
    BottomDestination(Routes.PROGRESS, R.string.nav_progress, Icons.Filled.Insights, Icons.Outlined.Insights),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * Root navigation graph.
 *
 * The bottom bar is rendered by the graph itself rather than by each screen, so
 * the four top-level destinations keep a single back-stack entry each
 * (`launchSingleTop` + `saveState`/`restoreState`) and the pushed destinations
 * (study, quiz, import) correctly hide it.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    settings: UserSettings,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // `route` is nullable and Set.contains takes a non-null key, so unwrap first.
    val showBottomBar = currentDestination?.route?.let {
        it in Routes.bottomBarDestinations
    } == true

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenPart = { deckId, partIndex ->
                        navController.navigate(Routes.study(deckId, partIndex))
                    },
                    onImport = { navController.navigate(Routes.IMPORT) },
                    onOpenCatalog = { navController.navigate(Routes.CATALOG) }
                )
            }

            composable(Routes.REVIEW) {
                ReviewScreen(container = container)
            }

            composable(Routes.PROGRESS) {
                ProgressScreen(container = container)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onOpenAbout = { navController.navigate(Routes.ABOUT) }
                )
            }

            composable(
                route = Routes.STUDY,
                arguments = listOf(
                    navArgument(Routes.ARG_DECK_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_PART_INDEX) { type = NavType.IntType }
                )
            ) { entry ->
                val deckId = entry.arguments?.getLong(Routes.ARG_DECK_ID) ?: 0L
                val partIndex = entry.arguments?.getInt(Routes.ARG_PART_INDEX) ?: 1
                StudyScreen(
                    container = container,
                    deckId = deckId,
                    partIndex = partIndex,
                    onStartQuiz = { navController.navigate(Routes.quiz(deckId, partIndex)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.QUIZ,
                arguments = listOf(
                    navArgument(Routes.ARG_DECK_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_PART_INDEX) { type = NavType.IntType }
                )
            ) { entry ->
                val deckId = entry.arguments?.getLong(Routes.ARG_DECK_ID) ?: 0L
                val partIndex = entry.arguments?.getInt(Routes.ARG_PART_INDEX) ?: 1
                QuizScreen(
                    container = container,
                    deckId = deckId,
                    partIndex = partIndex,
                    onFinished = { score, total ->
                        navController.navigate(
                            Routes.quizResult(deckId, partIndex, score, total)
                        ) {
                            popUpTo(Routes.QUIZ) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.QUIZ_RESULT,
                arguments = listOf(
                    navArgument(Routes.ARG_DECK_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_PART_INDEX) { type = NavType.IntType },
                    navArgument(Routes.ARG_SCORE) { type = NavType.IntType },
                    navArgument(Routes.ARG_TOTAL) { type = NavType.IntType }
                )
            ) { entry ->
                val deckId = entry.arguments?.getLong(Routes.ARG_DECK_ID) ?: 0L
                val partIndex = entry.arguments?.getInt(Routes.ARG_PART_INDEX) ?: 1
                val score = entry.arguments?.getInt(Routes.ARG_SCORE) ?: 0
                val total = entry.arguments?.getInt(Routes.ARG_TOTAL) ?: 0
                QuizResultScreen(
                    container = container,
                    deckId = deckId,
                    partIndex = partIndex,
                    score = score,
                    total = total,
                    onRetry = {
                        navController.navigate(Routes.quiz(deckId, partIndex)) {
                            popUpTo(Routes.QUIZ_RESULT) { inclusive = true }
                        }
                    },
                    onNextPart = {
                        navController.navigate(Routes.study(deckId, partIndex + 1)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                    onBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.IMPORT) {
                ImportScreen(
                    container = container,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.CATALOG) {
                ir.laxipatem.lexiflash.ui.home.CatalogScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
