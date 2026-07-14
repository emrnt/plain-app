package com.ismartcoding.plain.ui.page.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ismartcoding.plain.i18n.*
import com.ismartcoding.plain.tunnel.TunnelManager
import com.ismartcoding.plain.tunnel.TunnelEnabledPreference
import com.ismartcoding.plain.tunnel.NgrokAuthTokenPreference
import com.ismartcoding.plain.ui.base.BottomSpace
import com.ismartcoding.plain.ui.base.PCard
import com.ismartcoding.plain.ui.base.PListItem
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.PSwitch
import com.ismartcoding.plain.ui.base.PTopAppBar
import com.ismartcoding.plain.ui.base.Subtitle
import com.ismartcoding.plain.ui.base.Tips
import com.ismartcoding.plain.ui.base.TopSpace
import com.ismartcoding.plain.ui.base.VerticalSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelSettingsPage(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val tunnelUrl by TunnelManager.tunnelUrl.collectAsState()
    val tunnelRunning by TunnelManager.isRunning.collectAsState()

    var tunnelEnabled by remember { mutableStateOf(true) }
    var authToken by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        tunnelEnabled = TunnelEnabledPreference.getAsync()
        authToken = NgrokAuthTokenPreference.getAsync()
    }

    var showTokenDialog by remember { mutableStateOf(false) }
    var dialogInput by remember { mutableStateOf("") }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text(stringResource(Res.string.ngrok_auth_token)) },
            text = {
                OutlinedTextField(
                    value = dialogInput,
                    onValueChange = { dialogInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.ngrok_auth_token_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    authToken = dialogInput.trim()
                    scope.launch(Dispatchers.IO) { NgrokAuthTokenPreference.putAsync(authToken.trim()) }
                    showTokenDialog = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    PScaffold(topBar = {
        PTopAppBar(navController = navController, title = stringResource(Res.string.internet_access))
    }, content = { paddingValues ->
        LazyColumn(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
            item { TopSpace() }
            item {
                PCard {
                    PListItem(
                        title = stringResource(Res.string.enable_internet_access),
                        subtitle = stringResource(Res.string.ngrok_description)
                    ) {
                        PSwitch(activated = tunnelEnabled) { enable ->
                            tunnelEnabled = enable
                            scope.launch(Dispatchers.IO) {
                                TunnelEnabledPreference.putAsync(enable)
                                if (!enable) TunnelManager.stop()
                            }
                        }
                    }
                }
            }
            item {
                VerticalSpace(dp = 16.dp)
                Subtitle(text = stringResource(Res.string.configuration))
                PCard {
                    PListItem(
                        modifier = Modifier.clickable {
                            dialogInput = authToken
                            showTokenDialog = true
                        },
                        title = stringResource(Res.string.ngrok_auth_token),
                        subtitle = if (authToken.isNotEmpty()) "****${authToken.takeLast(4)}" else stringResource(Res.string.ngrok_token_required),
                        showMore = true
                    )
                }
            }
            if (tunnelEnabled && authToken.isNotEmpty()) {
                item {
                    VerticalSpace(dp = 16.dp)
                    Subtitle(text = stringResource(Res.string.status))
                    PCard {
                        PListItem(
                            title = stringResource(if (tunnelRunning) Res.string.connected else Res.string.disconnected),
                            subtitle = if (tunnelUrl.isNotEmpty()) tunnelUrl else stringResource(Res.string.ngrok_description)
                        )
                    }
                    if (tunnelRunning && tunnelUrl.isNotEmpty()) {
                        Tips(stringResource(Res.string.tunnel_url_tips, tunnelUrl))
                    }
                }
            }
            item { BottomSpace(paddingValues) }
        }
    })
}