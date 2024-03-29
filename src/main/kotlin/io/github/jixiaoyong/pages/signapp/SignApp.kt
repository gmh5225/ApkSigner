package io.github.jixiaoyong.pages.signapp

import ApkSigner
import LocalWindow
import Logger
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.github.jixiaoyong.beans.CommandResult
import io.github.jixiaoyong.beans.SignInfoBean
import io.github.jixiaoyong.pages.Routes
import io.github.jixiaoyong.utils.FileChooseUtil
import io.github.jixiaoyong.utils.showToast
import io.github.jixiaoyong.widgets.ButtonWidget
import io.github.jixiaoyong.widgets.HoverableTooltip
import io.github.jixiaoyong.widgets.InfoItemWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.util.*
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * @author : jixiaoyong
 * @description ：签名app的地方
 * 1. 选择/拖拽APP
 * 2. 开始签名/查看签名
 * 3. 签名历史
 *
 * 自动匹配apk签名的逻辑：
 * 1. 当前只选中了一个apk
 * 2. apk在apkSignatureMap中有对应的签名，并且该签名在signInfoBeans中有效
 *
 * @email : jixiaoyong1995@gmail.com
 * @date : 2023/8/18
 */

@Composable
fun PageSignApp(
    viewModel: SignAppViewModel,
    onChangePage: (String) -> Unit
) {
    val window = LocalWindow.current
    val clipboard = LocalClipboardManager.current

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()

    var signLogs by remember { mutableStateOf(listOf<String>()) }
    var signApkResult: CommandResult by remember { mutableStateOf(CommandResult.NOT_EXECUT) }

    val uiState by viewModel.uiState.collectAsState()

    val currentApkFilePath = uiState.currentApkFilePath
    val currentSingleApkPackageName = uiState.currentSingleApkPackageName
    val signedDirectory = uiState.signedDirectory

    var currentSignInfo by remember { mutableStateOf<SignInfoBean?>(null) }

    val changeCurrentApk: suspend (List<String>) -> Unit = { apkFiles ->
        signApkResult = CommandResult.EXECUTING
        viewModel.changeApkFilePath(apkFiles)
        signApkResult = CommandResult.NOT_EXECUT
        showToast("修改成功")
    }

    LaunchedEffect(
        uiState.signInfoBeans,
        uiState.globalSelectedSignInfo,
        currentSingleApkPackageName,
        uiState.isAutoMatchSignature,
        uiState.apkSignatureMap
    ) {
        val apkPackageName = currentSingleApkPackageName
        currentSignInfo = if (null == apkPackageName || !uiState.isAutoMatchSignature) {
            uiState.globalSelectedSignInfo
        } else {
            uiState.apkSignatureMap.get(apkPackageName)?.let { id ->
                uiState.signInfoBeans?.find { it.isValid() && it.id == id }
            } ?: uiState.globalSelectedSignInfo
        }

        Logger.log("apkPackageName:$apkPackageName currentSignInfo:$currentSignInfo ")
    }

    val local = uiState.signInfoResult
    when (local) {
        is CommandResult.Success<*> -> {
            Popup(onDismissRequest = {
                viewModel.changeSignInfo(CommandResult.NOT_EXECUT)
            }, alignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colors.onBackground.copy(0.2f))
                        .padding(horizontal = 50.dp, vertical = 65.dp)
                        .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 20.dp, vertical = 15.dp)
                ) {
                    Text(
                        "签名信息（鼠标上下滚动查看更多）",
                        color = MaterialTheme.colors.onSurface,
                        fontWeight = FontWeight.W800,
                        modifier = Modifier.padding(20.dp).align(alignment = Alignment.CenterHorizontally)
                    )
                    Column(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 450.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            Text(
                                local.result?.toString() ?: "",
                                color = MaterialTheme.colors.onSurface
                            )
                        }
                    }
                    TextButton(onClick = {
                        viewModel.changeSignInfo(CommandResult.NOT_EXECUT)
                    }, modifier = Modifier.align(alignment = Alignment.CenterHorizontally)) { Text("确认") }
                }
            }
        }

        is CommandResult.Error<*> -> {
            scope.launch {
                showToast("查询签名失败:${local.message}")
            }
        }

        else -> {}
    }

    if (local is CommandResult.EXECUTING || signApkResult is CommandResult.EXECUTING) {
        Popup(alignment = Alignment.Center) {
            Box(
                modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colors.onBackground.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.size(150.dp)
                        .background(color = Color.Black.copy(0.8f), shape = RoundedCornerShape(5.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(80.dp).padding(10.dp))
                    Text("处理中……", color = Color.White.copy(0.8f))
                }
            }
        }
    }

    Scaffold(scaffoldState = scaffoldState) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState())
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DropBoxPanel(
                    window,
                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(10.dp)
                        .background(
                            color = MaterialTheme.colors.surface,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .padding(15.dp)
                        .clickable {
                            scope.launch {
                                val chooseFileName =
                                    FileChooseUtil.chooseMultiFile(
                                        window,
                                        "请选择要签名的apk文件",
                                        filter = { _, name ->
                                            name.toLowerCase(Locale.getDefault()).endsWith(".apk")
                                        })
                                if (chooseFileName.isNullOrEmpty()) {
                                    showToast("请选择要签名的apk文件")
                                } else {
                                    if (!signedDirectory.isNullOrBlank()) {
                                        viewModel.saveSignedOutputDirectory(
                                            chooseFileName.first().substringBeforeLast(File.separator)
                                        )
                                    }
                                    changeCurrentApk(chooseFileName)
                                }
                            }
                        },
                    component = JPanel(),
                    onFileDrop = {
                        scope.launch {
                            val file = it.filter() {
                                it.lowercase(Locale.getDefault()).endsWith(".apk")
                            }
                            if (file.isNullOrEmpty()) {
                                showToast("请先选择正确的apk文件")
                            } else {
                                changeCurrentApk(file)
                            }
                        }
                    }
                ) {
                    Text(
                        text = "请拖拽apk文件到这里哦\n(支持多选，也可以点击这里选择apk文件)",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(alignment = Alignment.Center)
                    )
                }
                InfoItemWidget(
                    "当前选择的文件${if (currentApkFilePath.isEmpty()) "" else "(" + currentApkFilePath.size + ")"}",
                    if (currentApkFilePath.isEmpty()) "请先选择apk文件" else currentApkFilePath.joinToString("\n"),
                    buttonTitle = "查看签名",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (currentApkFilePath.isEmpty()) {
                                showToast("请先选择apk文件")
                            } else {
                                viewModel.changeSignInfo(CommandResult.EXECUTING)
                                val resultList = currentApkFilePath.map { ApkSigner.getApkSignInfo(it) }
                                val mergedResult = viewModel.mergeCommandResult(resultList, currentApkFilePath)
                                viewModel.changeSignInfo(mergedResult)
                            }
                        }
                    }
                )
                InfoItemWidget(
                    "当前的签名文件",
                    currentSignInfo?.toString() ?: "暂无",
                    onClick = {
                        onChangePage(Routes.SignInfo)
                        viewModel.removeApkSignature(currentSingleApkPackageName)
                    })

                val errorTips = "请先选择签名文件输出目录"
                InfoItemWidget(
                    "签名后的文件输出目录",
                    signedDirectory ?: errorTips,
                    buttonTitle = "修改目录",
                    onClick = {
                        scope.launch {
                            val outputDirectory =
                                FileChooseUtil.chooseSignDirectory(
                                    window,
                                    errorTips,
                                    signedDirectory ?: currentApkFilePath.firstOrNull()
                                )
                            if (outputDirectory.isNullOrBlank()) {
                                showToast(errorTips)
                            } else {
                                viewModel.saveSignedOutputDirectory(outputDirectory)
                                showToast("修改成功")
                            }
                        }
                    }
                )

                Text(
                    "签名方案：",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colors.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)
                        .padding(bottom = 15.dp)
                ) {
                    SignType.ALL_SIGN_TYPES.forEachIndexed { index, item ->
                        val isSelected = uiState.apkSignType.contains(item.type)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isSelected, onCheckedChange = {
                                val newTypes = mutableSetOf<Int>()
                                newTypes.addAll(uiState.apkSignType)
                                if (isSelected) {
                                    newTypes.remove(item.type)
                                } else {
                                    newTypes.add(item.type)
                                }

                                viewModel.changeApkSignType(newTypes)
                            })
                            Text(item.name, modifier = Modifier.padding(start = 5.dp))
                            HoverableTooltip(description = item.description) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "description information",
                                    modifier = it
                                )
                            }
                        }

                    }
                }

                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "是否开启对齐：",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colors.onPrimary
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 15.dp)
                    )

                    Checkbox(checked = uiState.isZipAlign, onCheckedChange = {
                        viewModel.changeZipAlign(it)
                    })
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(color = MaterialTheme.colors.surface.copy(alpha = 0.3f))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                ButtonWidget(
                    {
                        scope.launch(Dispatchers.IO) {
                            if (currentApkFilePath.filter { it.lowercase(Locale.getDefault()).endsWith(".apk") }
                                    .isNullOrEmpty()
                            ) {
                                showToast("请先选择正确的apk文件")
                                return@launch
                            }

                            val localSelectedSignInfo = currentSignInfo
                            if (null == localSelectedSignInfo || !localSelectedSignInfo.isValid()) {
                                onChangePage(Routes.SignInfo)
                                showToast("请先配置正确的签名文件")
                                return@launch
                            }

                            if (!ApkSigner.isInitialized()) {
                                onChangePage(Routes.SettingInfo)
                                showToast("请先配置apksigner和zipalign路径")
                                return@launch
                            }

                            if (uiState.apkSignType.isEmpty()) {
                                showToast("请至少选择一种签名方式")
                                return@launch
                            }

                            signApkResult = CommandResult.EXECUTING
                            val signResult = ApkSigner.alignAndSignApk(
                                currentApkFilePath,
                                localSelectedSignInfo.keyStorePath,
                                localSelectedSignInfo.keyAlias,
                                localSelectedSignInfo.keyStorePassword,
                                localSelectedSignInfo.keyPassword,
                                signedApkDirectory = signedDirectory,
                                zipAlign = uiState.isZipAlign,
                                signVersions = SignType.ALL_SIGN_TYPES.filter {
                                    uiState.apkSignType.contains(it.type)
                                },
                                onProgress = { line ->
                                    scope.launch {
                                        signLogs = mutableListOf<String>().apply {
                                            addAll(signLogs)
                                            add(line)
                                        }
                                    }
                                }
                            )

                            val mergedResult = viewModel.mergeCommandResult(signResult, currentApkFilePath)
                            signApkResult = mergedResult
                            val firstSuccessSignedApk =
                                signResult.firstOrNull { it is CommandResult.Success<*> } as CommandResult.Success<*>?

                            if (mergedResult is CommandResult.Success<*> && !firstSuccessSignedApk?.result?.toString()
                                    .isNullOrBlank()
                            ) {

                                launch(Dispatchers.IO) {
                                    if (uiState.isAutoMatchSignature && 1 == currentApkFilePath.size) {
                                        //  将当前签名和apk包名关联
                                        viewModel.updateApkSignatureMap(
                                            currentSingleApkPackageName,
                                            localSelectedSignInfo,
                                        )
                                    }
                                }

                                val result = scaffoldState.snackbarHostState.showSnackbar(
                                    "签名成功，是否打开签名后的文件？",
                                    "打开",
                                    SnackbarDuration.Long
                                )
                                val file = File(firstSuccessSignedApk?.result?.toString() ?: "")
                                if (SnackbarResult.ActionPerformed == result && file.exists()) {
                                    Desktop.getDesktop().open(file.parentFile)
                                }
                            } else if (mergedResult is CommandResult.Error<*>) {
                                val result = scaffoldState.snackbarHostState.showSnackbar(
                                    "签名失败，：${mergedResult.message}",
                                    "复制错误信息",
                                    SnackbarDuration.Indefinite
                                )
                                if (SnackbarResult.ActionPerformed == result) {
                                    clipboard.setText(AnnotatedString("${mergedResult.message}"))
                                }
                            }

                            signApkResult = CommandResult.NOT_EXECUT
                        }

                    },
                    enabled = CommandResult.NOT_EXECUT == signApkResult,
                    title = "开始签名apk",
                    modifier = Modifier.size(250.dp, 50.dp),
                )
            }
        }
    }
}


@Composable
fun DropBoxPanel(
    window: ComposeWindow,
    modifier: Modifier = Modifier,
    component: JPanel = JPanel(),
    onFileDrop: (List<String>) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {

    val dropBoundsBean = remember {
        mutableStateOf(DropBoundsBean())
    }

    Box(modifier = modifier.onPlaced {
        dropBoundsBean.value = DropBoundsBean(
            x = it.positionInWindow().x,
            y = it.positionInWindow().y,
            width = it.size.width,
            height = it.size.height
        )
    }) {
        LaunchedEffect(true) {
            component.setBounds(
                dropBoundsBean.value.x.roundToInt(),
                dropBoundsBean.value.y.roundToInt(),
                dropBoundsBean.value.width,
                dropBoundsBean.value.height
            )
            window.contentPane.add(component)

            object : DropTarget(component, object : DropTargetAdapter() {
                override fun drop(event: DropTargetDropEvent) {

                    event.acceptDrop(DnDConstants.ACTION_REFERENCE)
                    val dataFlavors = event.transferable.transferDataFlavors
                    dataFlavors.forEach {
                        if (it == DataFlavor.javaFileListFlavor) {
                            val list = event.transferable.getTransferData(it) as List<*>

                            val pathList = mutableListOf<String>()
                            list.forEach { filePath ->
                                pathList.add(filePath.toString())
                            }
                            onFileDrop(pathList)
                        }
                    }
                    event.dropComplete(true)
                }
            }) {

            }
        }

        SideEffect {
            component.setBounds(
                dropBoundsBean.value.x.roundToInt(),
                dropBoundsBean.value.y.roundToInt(),
                dropBoundsBean.value.width,
                dropBoundsBean.value.height
            )
        }

        DisposableEffect(true) {
            onDispose {
                window.contentPane.remove(component)
            }
        }

        content()
    }
}

data class DropBoundsBean(
    var x: Float = 0f, var y: Float = 0f, var width: Int = 0, var height: Int = 0
)
