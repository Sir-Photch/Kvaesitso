package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MissingPermissionBanner(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    secondaryAction: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(16.dp),
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .padding(end = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(8.dp)
            ) {
                secondaryAction()
                TextButton(
                    modifier = Modifier.padding(start = 8.dp),
                    onClick = onClick) {
                    Text("Grant", style = MaterialTheme.typography.labelLarge)
                }

            }

        }
    }
}