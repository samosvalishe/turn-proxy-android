@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.freeturn.app.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.freeturn.app.R
import com.freeturn.app.ui.components.InlineErrorCard
import com.freeturn.app.ui.components.SshFormFields
import com.freeturn.app.ui.theme.LocalReducedMotion
import com.freeturn.app.viewmodel.SetupSshDraft
import com.freeturn.app.ui.theme.Spacing

/**
 * Шаг 1 мастера: SSH-доступ к серверу. Во время проверки форма заменяется
 * busy-карточкой; ошибка — тональной карточкой под формой.
 */
@Composable
fun SetupSshStep(
    draft: SetupSshDraft,
    checking: Boolean,
    error: String?,
    showErrors: Boolean,
    onDraftChange: (SetupSshDraft) -> Unit
) {
    if (checking) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                BusyProgressIndicator()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        stringResource(R.string.setup_checking_ssh),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.setup_checking_ssh_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    SshFormFields(
        ip = draft.ip, onIpChange = { onDraftChange(draft.copy(ip = it)) },
        port = draft.port, onPortChange = { onDraftChange(draft.copy(port = it)) },
        username = draft.username, onUsernameChange = { onDraftChange(draft.copy(username = it)) },
        password = draft.password, onPasswordChange = { onDraftChange(draft.copy(password = it)) },
        authType = draft.authType, onAuthTypeChange = { onDraftChange(draft.copy(authType = it)) },
        sshKey = draft.sshKey, onSshKeyChange = { onDraftChange(draft.copy(sshKey = it)) },
        showErrors = showErrors
    )

    if (error != null) InlineErrorCard(error)
}

/** Wavy-индикатор; при reduced motion — обычная полоса без волновой анимации. */
@Composable
internal fun BusyProgressIndicator(modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        LinearWavyProgressIndicator(modifier = modifier.fillMaxWidth())
    }
}
