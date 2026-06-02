@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.freeturn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freeturn.app.R
import com.freeturn.app.data.SplitTunnelMode
import com.freeturn.app.ui.HapticUtil
import com.freeturn.app.ui.components.AppPickerDialog
import com.freeturn.app.ui.components.toPackageSet
import com.freeturn.app.viewmodel.SettingsViewModel

/**
 * Лист настройки split-tunneling, открываемый с главного экрана по ссылке под
 * статусом прокси. Режим и список приложений пишутся сразу в активный профиль
 * через ViewModel (без локального буфера и кнопки «сохранить»).
 */
@Composable
fun SplitTunnelSheetContent(
    settingsViewModel: SettingsViewModel,
    mode: String,
    apps: String,
    privacyMode: Boolean,
    @Suppress("UNUSED_PARAMETER") containerColor: Color
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val selectedCount = remember(apps) { apps.toPackageSet().size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.split_tunnel_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            stringResource(R.string.split_tunnel_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val options = listOf(
            SplitTunnelMode.ALL to stringResource(R.string.split_tunnel_all),
            SplitTunnelMode.INCLUDE to stringResource(R.string.split_tunnel_include),
            SplitTunnelMode.EXCLUDE to stringResource(R.string.split_tunnel_exclude)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = mode == value,
                    onClick = {
                        HapticUtil.perform(context, HapticUtil.Pattern.SELECTION)
                        settingsViewModel.setSplitTunnelMode(value)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) { Text(label) }
            }
        }

        if (mode != SplitTunnelMode.ALL) {
            FilledTonalButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    showPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !privacyMode
            ) {
                Icon(painterResource(R.drawable.manage_accounts_24px), null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.split_tunnel_pick_apps))
            }
            Text(
                stringResource(R.string.split_tunnel_apps_label) + ": $selectedCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(4.dp))
    }

    if (showPicker) {
        AppPickerDialog(
            selected = apps.toPackageSet(),
            onDismiss = { showPicker = false },
            onApply = { selected ->
                settingsViewModel.setSplitTunnelApps(selected.sorted().joinToString("\n"))
                showPicker = false
            }
        )
    }
}
