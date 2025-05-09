package com.ellison.flappybird

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.ActivityInfo
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ellison.flappybird.model.GameAction
import com.ellison.flappybird.model.GameStatus
import com.ellison.flappybird.ui.theme.FlappyBirdTheme
import com.ellison.flappybird.util.SplashScreenController
import com.ellison.flappybird.util.StatusBarUtil
import com.ellison.flappybird.view.Clickable
import com.ellison.flappybird.view.GameScreen
import com.ellison.flappybird.viewmodel.GameViewModel
import com.euxcet.viturering.RingManager
import com.euxcet.viturering.utils.GestureThrottle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    @Inject
    lateinit var ringManager: RingManager
    // `@RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        // Need to be called before setContentView or other view operation on the root view.
        val splashScreen = installSplashScreen()

        // Expand screen to status bar.
        StatusBarUtil.transparentStatusBar(this)

        setContent {
            FlappyBirdTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    val gameViewModel: GameViewModel = viewModel()

                    // Send a auto tick action to view model and trigger game start.
                    LaunchedEffect(key1 = Unit) {
                        while (isActive) {
                            delay(AutoTickDuration)
                            if (gameViewModel.viewState.value.gameStatus != GameStatus.Waiting) {
                                gameViewModel.dispatch(GameAction.AutoTick)
                            }
                        }
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(key1 = Unit) {
                        val observer = object : LifecycleObserver {
                            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                            fun onPause() {
                                // Todo send pause action
                            }

                            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                            fun onResume() {
                                // Todo send resume action
                            }
                        }

                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    Flappy(Clickable(

                        onStart = {
                            gameViewModel.dispatch(GameAction.Start)
                        },

                        onTap = {
                            gameViewModel.dispatch(GameAction.TouchLift)
                        },

                        onRestart = {
                            gameViewModel.dispatch(GameAction.Restart)
                        },

                        onExit = {
                            finish()
                        }
                    ))
                }
            }
        }

        SplashScreenController(splashScreen, viewModel).apply {
            customizeSplashScreen()
        }

        // Log.d("Splash", "onCreate() splashScreen:${getSplashScreen()}}")
    }

    override fun onStart() {
        super.onStart()
        connectRing()
    }

    override fun onStop() {
        super.onStop()
    }

    private fun connectRing() {
        ringManager.registerListener {
            onGestureCallback { // Gesture
                runOnUiThread {
                    Log.e("Nuix", "Gesture: $it")
                    if (GestureThrottle.throttle(it)) {
                        return@runOnUiThread
                    }
                    when (it) {
                        "index_flick" -> {
                            viewModel.dispatch(GameAction.Start)
                        }

                        "snap" -> {
                            finish()
                        }

                        "pinch" -> {
                            Log.e("FlappyMainActivity", "Pinch")
                            viewModel.dispatch(GameAction.TouchLift)
                        }

                        "circle_clockwise" -> {
                            viewModel.dispatch(GameAction.Restart)
                        }
                    }
                }
            }
            onMoveCallback {

            }
            ringManager.connect()
        }
    }
}

@Composable
fun Flappy(clickable: Clickable = Clickable()) {
    GameScreen(clickable = clickable)
}

const val AutoTickDuration = 50L // 300L Control bird and pipe speed.