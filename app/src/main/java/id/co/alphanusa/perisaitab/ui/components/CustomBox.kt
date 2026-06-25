package id.co.alphanusa.perisaitab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Container "tactical" dengan garis aksen vertikal di kiri & kanan.
 * Dipakai untuk menyeragamkan pola border kiri/kanan yang sebelumnya
 * ditulis ulang manual di banyak komponen.
 *
 * @param accentColor  warna garis aksen kiri/kanan
 * @param background   warna latar container
 * @param borderWidth  lebar garis aksen (default 4.dp)
 * @param shape        bentuk clip container (default sudut 2.dp)
 * @param onClick      opsional, membuat container bisa diklik
 */
@Composable
fun TacticalContainer(
    modifier: Modifier = Modifier,
    accentColor: Color = colorPrimary,
    background: Color = Color.Black.copy(alpha = 0.4f),
    borderWidth: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(2.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(background)
            .then(
                if (onClick != null)
                    Modifier.clickable() { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {

        // Accent line kiri
        Box(
            modifier = Modifier
                .width(borderWidth)
                .fillMaxHeight()
                .background(accentColor)
                .align(Alignment.CenterStart)
        )

        content()

        // Accent line kanan
        Box(
            modifier = Modifier
                .width(borderWidth)
                .fillMaxHeight()
                .background(accentColor)
                .align(Alignment.CenterEnd)
        )
    }
}
