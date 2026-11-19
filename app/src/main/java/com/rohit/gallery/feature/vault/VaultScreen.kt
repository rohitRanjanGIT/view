package com.rohit.gallery.feature.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.rohit.gallery.core.ui.components.FloatingActionPill
import com.rohit.gallery.core.ui.components.pillSlot
import com.rohit.gallery.core.model.albumCountLabel
import com.rohit.gallery.core.ui.components.ReachDown
import com.rohit.gallery.core.ui.components.rememberReachState
import androidx.compose.ui.graphics.graphicsLayer
import com.rohit.gallery.core.ui.components.AlbumSwitcherDrawer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.FloatingTab
import com.rohit.gallery.core.ui.components.FloatingTabBar
import com.rohit.gallery.core.ui.components.rememberChromeHaze
import com.rohit.gallery.core.ui.icons.GalleryIcons
import com.rohit.gallery.feature.albums.AlbumGrid
import com.rohit.gallery.feature.albums.rememberAlbumSelection
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.MediaListScreen
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * Locked: photos and videos moved into the app's private storage. Opens only after fingerprint, face or the phone's
 * screen lock; locks again as soon as it is left or the app is hidden. Can't be screenshotted or recorded.
 * Laid out like the main gallery: a Photos page and an Albums page side by side, swiped between or picked in the
 * floating tab bar. [onOpenMedia] opens the viewer on a photo, scoped to [album] when opened from one.
 * [onRelock] closes anything still showing a locked photo (the viewer layer) when it locks.
 */
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenMedia: (item: MediaItem, album: String?) -> Unit,
    onRelock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    SecureWindow()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val latestOnRelock by rememberUpdatedState(onRelock)
    // Leaving the screen or the app (also the screen going off) locks it again.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.lockNow()
        latestOnRelock()
    }

    if (unlocked) {
        VaultContent(viewModel, onBack, onOpenMedia)
    } else {
        LockedGate(onBack = onBack, onUnlocked = viewModel::onUnlocked)
    }
}

@Composable
private fun LockedGate(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val activity = LocalActivity.current
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var noScreenLock by rememberSaveable { mutableStateOf(false) }
    fun ask() {
        val a = activity ?: return
        promptVaultUnlock(a) { result ->
            when (result) {
                VaultAuthResult.Success -> onUnlocked()
                VaultAuthResult.NoScreenLock -> noScreenLock = true
                is VaultAuthResult.Failed -> message = result.message
            }
        }
    }
    // Ask straight away; the button is there if the prompt was dismissed.
    LaunchedEffect(Unit) { ask() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Text("Locked", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                if (noScreenLock) {
                    "Set up a screen lock (PIN, pattern, password or fingerprint) in your phone's settings to use Locked."
                } else {
                    "Photos and videos here are only in this app. Other apps and file managers can't see them."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (!noScreenLock) {
                Button(onClick = ::ask, modifier = Modifier.padding(top = 24.dp)) { Text("Unlock") }
            }
            UninstallNote(Modifier.padding(top = 32.dp))
        }
    }
}

/** Android runs no app code on uninstall, so the vault can't empty itself then: say how to get everything out. */
@Composable
private fun UninstallNote(modifier: Modifier = Modifier) {
    Text(
        "Before uninstalling Gallery or clearing its data, open Locked and use ⋮ > Unlock everything. " +
            "When uninstalling, choose to keep the app's data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private val VaultTabs = listOf(
    FloatingTab("Photos", GalleryIcons.PicturesTab),
    FloatingTab("Albums", GalleryIcons.AlbumsTab),
)

@Composable
private fun VaultContent(viewModel: VaultViewModel, onBack: () -> Unit, onOpenMedia: (MediaItem, String?) -> Unit) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    val bar: @Composable (Modifier) -> Unit = { modifier ->
        VaultItemsBar(
            count = selection.ids.size,
            onShare = {
                val chosen = viewModel.selectedItems()
                if (chosen.isNotEmpty()) runCatching { context.startActivity(viewModel.shareIntent(chosen)) }
            },
            onUnlock = { viewModel.unlock(viewModel.selectedItems()) },
            onDelete = { confirm = ConfirmRequest.Delete(viewModel.selectedItems()) },
            modifier = modifier,
        )
    }

    val album = openAlbum
    if (album != null) {
        // One locked album: its photos like Pictures, and the viewer swipes through just this album.
        BackHandler(enabled = !selection.isSelecting) { openAlbum = null }
        val albumItems = remember(items, album) { items?.filter { it.bucketName == album } }
        // The album emptied (all unlocked or deleted): back to the albums.
        LaunchedEffect(albumItems) { if (albumItems != null && albumItems.isEmpty()) openAlbum = null }
        val albums by viewModel.albums.collectAsStateWithLifecycle()
        val lockedAlbums = remember(albums) { albums.orEmpty().map { it.album } }
        // Swipe sideways on the grid to jump to another locked album.
        AlbumSwitcherDrawer(
            albums = lockedAlbums,
            currentId = lockedAlbums.firstOrNull { it.name == album }?.bucketId,
            enabled = !selection.isSelecting,
            onPick = {
                selection.clear()
                openAlbum = it.name
            },
        ) {
            MediaListScreen(
                title = album.ifBlank { "Unnamed" },
                emptyText = "",
                items = albumItems,
                selection = selection,
                onBack = { selection.clear(); openAlbum = null },
                onOpenMedia = { onOpenMedia(it, album) },
                asTimeline = true,
                contentKey = album,
                allowCopy = false,
                selectionBar = bar,
            )
        }
    } else {
        VaultPages(
            viewModel = viewModel,
            items = items,
            onBack = onBack,
            onOpenMedia = { onOpenMedia(it, null) },
            onOpenAlbum = { name ->
                selection.clear()
                openAlbum = name
            },
            onConfirm = { confirm = it },
            photoBar = bar,
        )
    }

    confirm?.let { request ->
        ConfirmDialog(
            request = request,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                when (request) {
                    is ConfirmRequest.Delete -> viewModel.deleteForever(request.items)
                    ConfirmRequest.UnlockAll -> viewModel.unlockAll()
                }
            },
        )
    }
    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Unlocking…") },
            text = {
                LinearProgressIndicator(
                    progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

/** The Photos and Albums pages, side by side like the main gallery, with the floating tab bar over them. */
@Composable
private fun VaultPages(
    viewModel: VaultViewModel,
    items: List<MediaItem>?,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onConfirm: (ConfirmRequest) -> Unit,
    photoBar: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState { VaultTabs.size }
    val scope = rememberCoroutineScope()
    val haze = rememberChromeHaze()
    val albumSelection = rememberAlbumSelection()
    val selecting = viewModel.selection.isSelecting || albumSelection.isSelecting
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var albumColumns by rememberSaveable { mutableIntStateOf(3) }
    val bottomInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    // A selection on one page ends when the other page is shown.
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage == 0) albumSelection.clear() else viewModel.selection.clear()
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !selecting,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
        ) { page ->
            when (page) {
                0 -> MediaListScreen(
                    title = "Locked",
                    emptyText = "Nothing locked yet. Select photos anywhere, then More > Lock.",
                    items = items,
                    selection = viewModel.selection,
                    onBack = onBack,
                    onOpenMedia = onOpenMedia,
                    asTimeline = true,
                    allowCopy = false,
                    titleActions = { VaultMenu(enabled = !items.isNullOrEmpty(), onUnlockAll = { onConfirm(ConfirmRequest.UnlockAll) }) },
                    selectionBar = photoBar,
                )
                else -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        val reach = rememberReachState()
                        VaultTitleBar("Locked albums", onBack, titleAlpha = { 1f - reach.progress })
                        val list = albums
                        when {
                            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                                Text(
                                    "Lock an album from the Albums tab (long-press it, then Lock) and it shows here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            else -> ReachDown(title = "Locked albums", subtitle = albumCountLabel(list.size), state = reach) {
                                AlbumGrid(
                                    list = list,
                                    columns = albumColumns,
                                    selection = albumSelection,
                                    contentBottomPadding = VaultBarRoom + bottomInset,
                                    onOpenAlbum = { onOpenAlbum(it.name) },
                                    onColumnsChange = { albumColumns = it },
                                )
                            }
                        }
                    }
                    if (albumSelection.isSelecting) {
                        BackHandler { albumSelection.clear() }
                        val chosenAlbums = albumSelection.ids
                        VaultItemsBar(
                            count = chosenAlbums.size,
                            onShare = {
                                val chosen = viewModel.itemsInAlbums(chosenAlbums)
                                if (chosen.isNotEmpty()) runCatching { context.startActivity(viewModel.shareIntent(chosen)) }
                            },
                            onUnlock = {
                                viewModel.unlock(viewModel.itemsInAlbums(chosenAlbums))
                                albumSelection.clear()
                            },
                            onDelete = {
                                onConfirm(ConfirmRequest.Delete(viewModel.itemsInAlbums(chosenAlbums)))
                                albumSelection.clear()
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        if (!selecting) {
            FloatingTabBar(
                tabs = VaultTabs,
                selectedIndex = pager.targetPage,
                position = { pager.currentPage + pager.currentPageOffsetFraction },
                onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
                haze = haze,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Room the album grid keeps under its last row for the floating tab bar. */
private val VaultBarRoom = 92.dp

@Composable
private fun VaultTitleBar(title: String, onBack: () -> Unit, titleAlpha: () -> Float = { 1f }) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { alpha = titleAlpha() })
        }
    }
}

@Composable
private fun VaultMenu(enabled: Boolean, onUnlockAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Unlock everything") }, enabled = enabled, onClick = { open = false; onUnlockAll() })
        }
    }
}

/** Share / Unlock / Delete, for selected photos or for every photo in the selected albums. */
@Composable
private fun VaultItemsBar(count: Int, onShare: () -> Unit, onUnlock: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionPill(modifier) {
        ActionItem(Icons.Filled.Share, "Share", count > 0, onShare, Modifier.pillSlot())
        ActionItem(GalleryIcons.LockOpen, "Unlock", count > 0, onUnlock, Modifier.pillSlot())
        ActionItem(Icons.Filled.Delete, "Delete", count > 0, onDelete, Modifier.pillSlot())
    }
}

private sealed interface ConfirmRequest {
    data class Delete(val items: List<MediaItem>) : ConfirmRequest

    data object UnlockAll : ConfirmRequest
}

@Composable
private fun ConfirmDialog(request: ConfirmRequest, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    when (request) {
        is ConfirmRequest.Delete -> {
            val n = request.items.size
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Delete permanently?") },
                text = {
                    Text(
                        (if (n == 1) "This item is" else "These $n items are") +
                            " only in Locked, so this deletes the only copy. This can't be undone.",
                    )
                },
                confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        ConfirmRequest.UnlockAll -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Unlock everything?") },
            text = {
                Text(
                    "Every photo and video goes back to the album it was locked from, where other apps can see it again. " +
                        "Do this before uninstalling Gallery or clearing its data.",
                )
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Unlock all") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
