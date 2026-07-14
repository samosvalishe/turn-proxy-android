@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.ui.components.SettingsBackButton
import com.freeturn.app.ui.components.SettingsContentMaxWidth
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.viewmodel.server.ServerSetupViewModel
import com.freeturn.app.viewmodel.server.SetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import com.freeturn.app.ui.theme.Spacing

@Composable
fun ServerSetupScreen(
    onClose: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ServerSetupViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val install = state.install
    val done = install?.done == true
    val defaultName = stringResource(R.string.add_default_server_name)

    var showAbortDialog by rememberSaveable { mutableStateOf(false) }
    var highlightErrors by rememberSaveable { mutableStateOf(false) }

    val backAction: () -> Unit = when {
        state.step == SetupStep.Ssh -> onClose
        state.step == SetupStep.Config -> viewModel::backToSsh
        done -> onFinished
        install?.error != null -> viewModel::backToConfig
        else -> ({ showAbortDialog = true })
    }
    // Первый шаг не перехватываем для работы predictive back.
    BackHandler(enabled = state.step != SetupStep.Ssh) { backAction() }

    if (showAbortDialog) {
        AlertDialog(
            onDismissRequest = { showAbortDialog = false },
            title = { Text(stringResource(R.string.setup_abort_title)) },
            text = { Text(stringResource(R.string.setup_abort_text)) },
            confirmButton = {
                TextButton(onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    showAbortDialog = false
                    viewModel.backToConfig()
                }) { Text(stringResource(R.string.setup_abort_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAbortDialog = false }) {
                    Text(stringResource(R.string.setup_abort_stay))
                }
            }
        )
    }

    val scope = rememberCoroutineScope()
    val wgFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (!text.isNullOrBlank()) {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    viewModel.setConfig(viewModel.uiState.value.config.copy(wgConfText = text))
                }
            }
        }
    }

    val fab: SetupFab? = when {
        state.step == SetupStep.Ssh && !state.checkingSsh ->
            SetupFab(R.string.setup_continue, R.drawable.chevron_right_24px) {
                if (state.ssh.valid) {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    highlightErrors = false
                    viewModel.submitSsh()
                } else {
                    HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                    highlightErrors = true
                }
            }
        state.step == SetupStep.Config ->
            SetupFab(R.string.setup_install_btn, R.drawable.cloud_download_24px) {
                if (state.configValid) {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    highlightErrors = false
                    viewModel.submitConfig(defaultName)
                } else {
                    HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                    highlightErrors = true
                }
            }
        done -> SetupFab(R.string.setup_go_home, R.drawable.home_24px) {
            HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
            onFinished()
        }
        else -> null
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(state.step) {
        scrollState.scrollTo(0)
        highlightErrors = false
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(when {
                        done -> R.string.setup_done_title
                        state.step == SetupStep.Ssh -> R.string.connect_to_server
                        state.step == SetupStep.Config -> R.string.setup_config_title
                        else -> R.string.setup_install_title
                    }))
                },
                subtitle = if (done) null else {
                    { StepProgressCapsules(current = state.step.ordinal) }
                },
                navigationIcon = { SettingsBackButton(backAction) },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            // Последний ненулевой fab держим для exit-анимации.
            val shownFab = remember { mutableStateOf(fab) }
            if (fab != null) shownFab.value = fab
            AnimatedVisibility(
                visible = fab != null,
                enter = if (reducedMotion) EnterTransition.None else scaleIn() + fadeIn(),
                exit = if (reducedMotion) ExitTransition.None else scaleOut() + fadeOut()
            ) {
                shownFab.value?.let { f ->
                    ExtendedFloatingActionButton(
                        onClick = f.onClick,
                        icon = { Icon(painterResource(f.iconRes), contentDescription = null) },
                        text = { Text(stringResource(f.labelRes)) }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        // Shared-axis X: вперёд - слайд влево, назад - вправо.
                        val forward = targetState.ordinal > initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (fadeIn(effectsSpec) +
                            slideInHorizontally(spatialSpec) { dir * it / 5 }) togetherWith
                            (fadeOut(effectsSpec) +
                                slideOutHorizontally(spatialSpec) { -dir * it / 12 })
                    }
                },
                label = "setup_step",
                modifier = Modifier
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxWidth()
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    when (step) {
                        SetupStep.Ssh -> SetupSshStep(
                            draft = state.ssh,
                            checking = state.checkingSsh,
                            error = state.sshError,
                            showErrors = highlightErrors,
                            onDraftChange = viewModel::setSsh
                        )
                        SetupStep.Config -> SetupConfigStep(
                            draft = state.config,
                            wgDetectedPort = state.wgDetectedPort,
                            duplicateHost = state.duplicateHost,
                            portsClash = state.portsClash,
                            showErrors = highlightErrors,
                            onDraftChange = viewModel::setConfig,
                            onRollListenPort = viewModel::rollListenPort,
                            onRollWgPort = viewModel::rollWgPort,
                            onLoadWgFile = { wgFilePicker.launch("*/*") }
                        )
                        SetupStep.Install -> SetupInstallStep(
                            install = state.install,
                            onRetry = viewModel::retryInstall,
                            onBackToConfig = viewModel::backToConfig
                        )
                    }
                }
            }
            val clearance by animateDpAsState(
                targetValue = if (fab != null) 88.dp else 12.dp,
                animationSpec = if (reducedMotion) snap() else spring(),
                label = "fab_clearance"
            )
            Spacer(Modifier.height(clearance))
        }
    }
}

@Composable
private fun StepProgressCapsules(current: Int, total: Int = 3) {
    val description = stringResource(R.string.setup_step_counter, current + 1, total)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .padding(top = Spacing.sm)
            .width(96.dp)
            // Капсулы декоративны - TalkBack читает "Шаг N из M".
            .clearAndSetSemantics { contentDescription = description }
    ) {
        repeat(total) { i ->
            val color by animateColorAsState(
                targetValue = if (i <= current) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "step_capsule"
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

private class SetupFab(
    val labelRes: Int,
    val iconRes: Int,
    val onClick: () -> Unit
)
