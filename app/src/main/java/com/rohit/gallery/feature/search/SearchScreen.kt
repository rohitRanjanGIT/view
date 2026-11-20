package com.rohit.gallery.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.MediaTimeline
import com.rohit.gallery.feature.selection.SelectionActionsBar
import com.rohit.gallery.feature.selection.SelectionTopBar
import com.rohit.gallery.feature.timeline.GridLevel
import java.text.NumberFormat

/**
 * Search: a field at the top (keyboard up on arrival), ready-made searches until something is typed, then the
 * matches as a dated grid with the usual selection actions. [onOpenMedia] gets the query so the viewer can
 * page through the same matches.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, query: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    var text by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(GridLevel.Default) }
    // Raise the keyboard only on first arrival, not when coming back from the viewer.
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection = viewModel.selection

    LaunchedEffect(text) { viewModel.setQuery(text) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusRequester.requestFocus()
            focusedOnce = true
        }
    }
    BackHandler(enabled = selection.isSelecting) { selection.clear() }

    // Scrolling the results puts the keyboard away so they can be seen.
    val hideKeyboardOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (fieldFocused) focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selection.isSelecting) {
                SelectionTopBar(
                    count = selection.ids.size,
                    onClose = selection::clear,
                    onSelectAll = { (state as? SearchUiState.Results)?.items?.let(selection::selectAll) },
                )
            } else {
                SearchField(
                    text = text,
                    onTextChange = { text = it },
                    onBack = onBack,
                    onSubmit = {
                        viewModel.saveRecent(text)
                        focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { fieldFocused = it.isFocused },
                )
            }

            when (val s = state) {
                SearchUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is SearchUiState.Start -> StartContent(
                    state = s,
                    onPick = { text = it },
                    onClearRecent = viewModel::clearRecent,
                )
                is SearchUiState.Results -> if (s.items.isEmpty()) {
                    NoResults(s.query)
                } else {
                    Column(Modifier.fillMaxSize().nestedScroll(hideKeyboardOnScroll)) {
                        ResultCount(s.items.size)
                        MediaTimeline(s.items, selection, level, { level = it }) { item ->
                            viewModel.saveRecent(s.query)
                            focusManager.clearFocus()
                            onOpenMedia(item, s.query)
                        }
                    }
                }
            }
        }

        if (selection.isSelecting) {
            val allFavorite by remember(state) {
                derivedStateOf {
                    val items = (state as? SearchUiState.Results)?.items.orEmpty()
                    val ids = selection.ids
                    ids.isNotEmpty() && items.filter { it.id in ids }.let { sel -> sel.isNotEmpty() && sel.all { it.isFavorite } }
                }
            }
            SelectionActionsBar(
                controller = viewModel.selectionActions,
                selection = selection,
                allFavorite = allFavorite,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Dates, albums, file names, videos…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { onTextChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = modifier.weight(1f),
            )
        }
    }
}

/** Recent searches, then kinds, albums and years to tap. */
@Composable
private fun StartContent(state: SearchUiState.Start, onPick: (String) -> Unit, onClearRecent: () -> Unit) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
    ) {
        if (state.recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent", Modifier.weight(1f))
                TextButton(onClick = onClearRecent) { Text("Clear") }
            }
            state.recent.forEach { query ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        ChipSection("Types", state.suggestions.kinds, numbers, onPick)
        ChipSection("Albums", state.suggestions.albums, numbers, onPick)
        ChipSection("Years", state.suggestions.years, numbers, onPick)
    }
}

@Composable
private fun ChipSection(title: String, items: List<Suggestion>, numbers: NumberFormat, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    SectionTitle(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { suggestion ->
            SuggestionChip(
                onClick = { onPick(suggestion.query) },
                label = {
                    Text(
                        "${suggestion.label}  ${numbers.format(suggestion.count)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun ResultCount(count: Int) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Text(
        if (count == 1) "1 match" else "${numbers.format(count)} matches",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NoResults(query: String) {
    Column(
        Modifier.fillMaxSize().imePadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing matches “$query”", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Try a date (March 2024, last week), an album name, a file name, or videos / screenshots.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
