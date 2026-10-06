package io.nekohasekai.sfa.compose.component

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R

class PxlCatEasterEggState {
    var isVisible by mutableStateOf(false)
        private set
    private var tapCount = 0
    private var lastTapAt = 0L

    fun tap(context: Context) {
        val now = SystemClock.elapsedRealtime()
        tapCount = if (now - lastTapAt > 10_000) 1 else tapCount + 1
        lastTapAt = now
        val hint = when (tapCount) {
            3 -> R.string.pxlnet_cat_hint_more
            5 -> R.string.pxlnet_cat_hint_almost
            7 -> R.string.pxlnet_cat_hint_ready
            else -> null
        }
        if (hint != null) Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
        if (tapCount >= 8) {
            tapCount = 0
            isVisible = true
        }
    }

    fun dismiss() {
        isVisible = false
    }
}

@Composable
fun rememberPxlCatEasterEggState(): PxlCatEasterEggState = remember { PxlCatEasterEggState() }

@Composable
fun PxlCatEasterEggDialog(state: PxlCatEasterEggState) {
    if (!state.isVisible) return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(stringResource(R.string.pxlnet_cat_found)) },
        text = {
            Image(
                painter = painterResource(R.drawable.pxl_secret_cat),
                contentDescription = stringResource(R.string.pxlnet_cat_photo),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit,
            )
        },
        confirmButton = {
            TextButton(onClick = state::dismiss) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
