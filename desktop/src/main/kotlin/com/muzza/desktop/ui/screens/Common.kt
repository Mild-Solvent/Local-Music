package com.muzza.desktop.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muzza.desktop.ui.theme.Sea

@Composable
fun scrollbarStyle() = ScrollbarStyle(
    minimalHeight = 40.dp, thickness = 8.dp, shape = RoundedCornerShape(4.dp), hoverDurationMillis = 300,
    unhoverColor = Sea.Cream.copy(alpha = 0.45f), hoverColor = Sea.Cream.copy(alpha = 0.9f),
)

/** A scrolling page with a soft scrollbar on the right. */
@Composable
fun Page(state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp, end = 10.dp), content = content)
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp), style = scrollbarStyle())
    }
}

/** A sideways row of cards with its own scrollbar (mouse wheel + shift, or drag the bar). */
@Composable
fun <T> CardRow(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    val state = rememberLazyListState()
    Column {
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items.size, key = { key(items[it]) }) { card(items[it]) }
        }
        HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), style = scrollbarStyle())
    }
}

/** Picture + two lines, for albums, playlists and artists. */
@Composable
fun Card(title: String, sub: String, width: Dp = 160.dp, center: Boolean = false, onClick: () -> Unit, art: @Composable () -> Unit) {
    Column(
        Modifier.width(width).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(bottom = 6.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        art()
        Spacer(Modifier.height(8.dp))
        Text(title, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (center) TextAlign.Center else TextAlign.Start)
        Text(sub, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = Sea.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun Modifier.pageTitlePadding() = this.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
