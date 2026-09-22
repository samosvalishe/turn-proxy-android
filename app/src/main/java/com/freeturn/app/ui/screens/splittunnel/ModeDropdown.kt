package com.freeturn.app.ui.screens.splittunnel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.data.config.SplitTunnelMode
import com.freeturn.app.ui.components.ChoiceOption
import com.freeturn.app.ui.components.OptionDropdown

/** Выбор режима split-tunnel: include / exclude. */
@Composable
internal fun ModeDropdown(
    mode: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OptionDropdown(
        label = stringResource(R.string.split_tunnel_mode_label),
        options = listOf(
            ChoiceOption(SplitTunnelMode.INCLUDE, stringResource(R.string.split_tunnel_mode_include)),
            ChoiceOption(SplitTunnelMode.EXCLUDE, stringResource(R.string.split_tunnel_mode_exclude))
        ),
        selected = mode,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled
    )
}
