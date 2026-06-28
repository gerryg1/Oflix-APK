package com.oflix.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oflix.app.data.MediaItem
import com.oflix.app.ui.theme.TextMuted
import com.oflix.app.ui.components.shimmerEffect
import androidx.compose.ui.draw.clip

@Composable
fun HorizontalSection(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onSeeMoreClick: (() -> Unit)? = null,
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            if (onSeeMoreClick != null) {
                TextButton(
                    onClick = onSeeMoreClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Lihat Semua",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLoading) {
                items(5) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .aspectRatio(2f / 3f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .shimmerEffect()
                    )
                }
            } else {
                items(items) { item ->
                    MovieCard(movie = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}
