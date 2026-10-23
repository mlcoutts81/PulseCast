package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun PodcastArtwork(
    artworkUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    showTitleOverlayOnFallback: Boolean = false
) {
    val gradientColors = remember(title) {
        val palettes = listOf(
            listOf(Color(0xFF3F51B5), Color(0xFF00ACC1)),
            listOf(Color(0xFF5E35B1), Color(0xFFD81B60)),
            listOf(Color(0xFF00897B), Color(0xFF43A047)),
            listOf(Color(0xFFE65100), Color(0xFFFFB300)),
            listOf(Color(0xFF1E88E5), Color(0xFF3949AB)),
            listOf(Color(0xFF6D4C41), Color(0xFF26A69A))
        )
        val index = abs(title.hashCode()) % palettes.size
        palettes[index]
    }

    val initials = remember(title) {
        title.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "PC" }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (showTitleOverlayOnFallback) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(6.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxSize(0.45f)
            )
        }

        if (artworkUrl.isNotBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
