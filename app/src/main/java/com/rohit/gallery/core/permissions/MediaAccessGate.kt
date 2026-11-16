package com.rohit.gallery.core.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shows [content] once the app can read media. Otherwise shows an explanation and the right
 * action for the current state. With partial access, a banner offers to upgrade to full access.
 */
@Composable
fun MediaAccessGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberMediaAccessState()
    when (state.access) {
        MediaAccess.Full -> Box(modifier) { content() }
        MediaAccess.Partial -> Column(modifier) {
            PartialAccessBanner(onAllowAll = state::request)
            Box(Modifier.weight(1f)) { content() }
        }
        MediaAccess.Denied -> PermissionScreen(
            message = "Gallery needs access to your photos and videos to show them. They never leave your device.",
            actionLabel = "Allow access",
            onAction = state::request,
            modifier = modifier,
        )
        MediaAccess.DeniedPermanently -> PermissionScreen(
            message = "Photo and video access is turned off. Open settings, choose Permissions, then allow Photos and videos.",
            actionLabel = "Open settings",
            onAction = state::openAppSettings,
            modifier = modifier,
        )
    }
}

@Composable
private fun PermissionScreen(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your gallery", style = MaterialTheme.typography.headlineMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun PartialAccessBanner(onAllowAll: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Only selected photos are visible.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllowAll) { Text("Allow all") }
        }
    }
}
