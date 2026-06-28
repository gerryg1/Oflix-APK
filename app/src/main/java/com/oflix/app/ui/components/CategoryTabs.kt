package com.oflix.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oflix.app.ui.theme.PrimaryRed
import com.oflix.app.ui.theme.TextMuted

@Composable
fun CategoryTabs(
    currentCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf(
        Pair("🏠 Beranda", "home"),
        Pair("🎬 Film", "film"),
        Pair("📺 Series", "series"),
        Pair("🐲 Anichin", "donghua"),
        Pair("📚 Komik", "komik")
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { (label, route) ->
            val isSelected = currentCategory == route
            
            Text(
                text = label,
                color = if (isSelected) Color.White else TextMuted,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                modifier = Modifier
                    .clickable { onCategorySelected(route) }
                    .padding(vertical = 8.dp)
            )
        }
    }
}
