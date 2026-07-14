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
import com.ismartcoding.plain.tunnel.FrpServerPreference
import com.ismartcoding.plain.tunnel.FrpPortPreference
import com.ismartcoding.plain.tunnel.FrpDomainPreference
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
    var serverAddr by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("7000") }
    var domain by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        tunnelEnabled = TunnelEnabledPreference.getAsync()
        serverAddr = FrpServerPreference.getAsync()
        serverPort = FrpPortPreference.getAsync()
        domain = FrpDomainPreference.getAsync()
    }

    var showDialog by remember { mutableStateOf("") }
    var dialogInput by remember { mutableStateOf("") }

    if (showDialog.isNotEmpty()) {
        val label = when (showDialog) {
            "server" -> stringResource(Res.string.frp_server)
            "port" -> stringResource(Res.string.frp_port)
            "domain" -> stringResource(Res.string.frp_domain)
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { showDialog = "" },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = dialogInput,
                    onValueChange = { dialogInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(label) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = dialogInput.trim()
                    scope.launch(Dispatchers.IO) {
                        when (showDialog) {
                            "server" -> { serverAddr = trimmed; FrpServerPreference.putAsync(trimmed) }
                            "port" -> { serverPort = trimmed; FrpPortPreference.putAsync(trimmed) }
                            "domain" -> { domain = trimmed; FrpDomainPreference.putAsync(trimmed) }
                        }
                    }
                    showDialog = ""
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = "" }) { Text(stringResource(Res.string.cancel)) }
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
                        subtitle = stringResource(Res.string.frp_description)
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
                Subtitle(text = stringResource(Res.string.frp_vps_config))
                PCard {
                    PListItem(
                        modifier = Modifier.clickable {
                            dialogInput = serverAddr; showDialog = "server"
                        },
                        title = stringResource(Res.string.frp_server),
                        subtitle = serverAddr.ifEmpty { stringResource(Res.string.frp_required) },
                        showMore = true
                    )
                    PListItem(
                        modifier = Modifier.clickable {
                            dialogInput = serverPort; showDialog = "port"
                        },
                        title = stringResource(Res.string.frp_port),
                        subtitle = serverPort.ifEmpty { "7000" },
                        showMore = true
                    )
                    PListItem(
                        modifier = Modifier.clickable {
                            dialogInput = domain; showDialog = "domain"
                        },
                        title = stringResource(Res.string.frp_domain),
                        subtitle = domain.ifEmpty { stringResource(Res.string.frp_optional) },
                        showMore = true
                    )
                }
            }
            if (tunnelEnabled && serverAddr.isNotEmpty()) {
                item {
                    VerticalSpace(dp = 16.dp)
                    Subtitle(text = stringResource(Res.string.status))
                    PCard {
                        PListItem(
                            title = stringResource(if (tunnelRunning) Res.string.connected else Res.string.disconnected),
                            subtitle = if (tunnelUrl.isNotEmpty()) tunnelUrl else stringResource(Res.string.frp_description)
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