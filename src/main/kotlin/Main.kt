
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cafe.adriel.lyricist.Lyricist
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import cafe.adriel.lyricist.strings
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ExclamationTriangle
import io.github.jixiaoyong.beans.AppState
import io.github.jixiaoyong.i18n.Strings
import io.github.jixiaoyong.pages.App
import io.github.jixiaoyong.utils.AppProcessUtil
import io.github.jixiaoyong.utils.SettingsTool
import io.github.jixiaoyong.widgets.PopWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.system.exitProcess

val LocalWindow = compositionLocalOf<ComposeWindow> { error("No Window provided") }
val LocalSettings = compositionLocalOf<SettingsTool> { error("No SettingsTool provided") }
val LocalLyricist = compositionLocalOf<Lyricist<Strings>> { error("No SettingsTool provided") }

fun main() =
    application {
        val windowState = rememberWindowState(height = 650.dp, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = ::exitApplication,
            title = "APK Signer",
            icon = painterResource("/imgs/icon.png"),
            state = windowState,
        ) {
            var appState by remember { mutableStateOf<AppState>(AppState.Idle) }
            var checkDualRunning by remember { mutableStateOf(true) }
            val settingsTool = SettingsTool(scope = rememberCoroutineScope())
            val stringsLyricist = rememberStrings()

            // app语言：优先用户选择的，其次系统默认的，其次英文
            val systemLanguage = Locale.getDefault().language
            val language by settingsTool.language.collectAsState(systemLanguage)
            if (null != language) {
                stringsLyricist.languageTag = language.toString()
            }

            LaunchedEffect(Unit) {
                window.minimumSize = window.size
            }
            ProvideStrings(stringsLyricist) {
                CompositionLocalProvider(
                    LocalWindow provides window,
                    LocalSettings provides settingsTool,
                    LocalLyricist provides stringsLyricist
                ) {

                    LaunchedEffect(checkDualRunning) {
                        appState = AppState.Loading
                        val isAppRunning = withContext(Dispatchers.IO) {
                            AppProcessUtil.isDualAppRunning("ApkSigner")
                        }
                        appState = if (isAppRunning) {
                            AppState.AlreadyExists
                        } else {
                            AppState.Success
                        }
                    }

                    when (appState) {
                        is AppState.Idle, AppState.Loading -> {
                            LoadingPage()
                        }

                        is AppState.AlreadyExists -> {
                            AlreadyExistsPage { checkDualRunning = !checkDualRunning }
                        }

                        AppState.Success -> {
                            App()
                        }
                    }
                }
            }
        }
    }

@Composable
fun LoadingPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(strings.loading, Modifier.padding(top = 10.dp))
    }
}

@Composable
fun AlreadyExistsPage(tryAgainFunc: () -> Unit) {

    Box(modifier = Modifier.background(color = MaterialTheme.colors.background).fillMaxSize()) {
        PopWidget(title = "",
            show = true,
            confirmButton = strings.retry,
            cancelButton = strings.exit,
            onDismiss = { exitProcess(0) },
            onConfirm = { tryAgainFunc() }) {
            Column(
                modifier = Modifier.wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    FontAwesomeIcons.Solid.ExclamationTriangle,
                    tint = MaterialTheme.colors.error,
                    contentDescription = "already exists",
                    modifier = Modifier.size(50.dp),
                )
                Text(strings.alreadyRunning, Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Preview
@Composable
private fun PrevLoading() {
    LoadingPage()
}

@Preview
@Composable
private fun PrevAlreadyExists() {
    AlreadyExistsPage {}
}
