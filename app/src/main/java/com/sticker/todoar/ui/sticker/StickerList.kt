package com.sticker.todoar.ui.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sticker.todoar.R
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.model.StickerUiState
import com.sticker.todoar.ui.model.UiText
import com.sticker.todoar.ui.model.asString

@Composable
internal fun StickerList(
    state: StickerUiState,
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (state) {
            is StickerUiState.Success -> {
                if (state.stickers.isEmpty()) {
                    item {
                        EmptyStickerState()
                    }
                } else {
                    stickerItems(
                        state = state,
                        onEditSticker = onEditSticker,
                        onSnoozeSticker = onSnoozeSticker,
                        onToggleSticker = onToggleSticker,
                        onDeleteSticker = onDeleteSticker
                    )
                }
            }
            is StickerUiState.Error -> {
                item {
                    ErrorStickerState(state.message)
                }
            }
            is StickerUiState.Loading -> {
                item {
                    LoadingStickerState()
                }
            }
        }
    }
}

private fun LazyListScope.stickerItems(
    state: StickerUiState.Success,
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    items(
        items = state.stickers,
        key = { sticker -> sticker.id }
    ) { sticker ->
        StickerItem(
            sticker = sticker,
            nowMillis = state.nowMillis,
            onEditSticker = onEditSticker,
            onSnoozeSticker = onSnoozeSticker,
            onToggleSticker = onToggleSticker,
            onDeleteSticker = onDeleteSticker
        )
    }
}

@Composable
private fun ErrorStickerState(message: UiText) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message.asString(),
            color = Color(0xFF8B2F1D),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingStickerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color(0xFF2F6B5F)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading_stickers),
            color = Color(0xFF5A6259),
            fontSize = 17.sp
        )
    }
}

@Composable
private fun EmptyStickerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.empty_stickers),
            color = Color(0xFF5A6259),
            fontSize = 17.sp
        )
    }
}
