package dev.tabbyrush.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tabbyrush.shared.theme.TabbyRushThemeTokens

@Composable
fun LicenseNotices(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(TabbyRushThemeTokens.SpacingLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(TabbyRushThemeTokens.SpacingLarge)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Source Notices",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = TabbyRushThemeTokens.SpacingMedium),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                NoticeItem(
                    title = "Compose Multiplatform & Material 3",
                    license = "Apache License 2.0",
                    copyright = "JetBrains s.r.o. and The Android Open Source Project",
                    description = "Declarative multiplatform user interface framework for Kotlin."
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                NoticeItem(
                    title = "AndroidX Room & Bundled SQLite",
                    license = "Apache License 2.0",
                    copyright = "The Android Open Source Project",
                    description = "Robust multiplatform SQLite object mapping and database migration runtime."
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                NoticeItem(
                    title = "Kotlinx Coroutines, Serialization & DateTime",
                    license = "Apache License 2.0",
                    copyright = "JetBrains s.r.o.",
                    description = "Asynchronous structured concurrency, JSON serialization, and ISO-8601 date-time manipulation."
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                NoticeItem(
                    title = "Ktor HTTP Client",
                    license = "Apache License 2.0",
                    copyright = "JetBrains s.r.o.",
                    description = "Asynchronous multiplatform HTTP networking client with OkHttp and Java engine bindings."
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                NoticeItem(
                    title = "Java Native Access (JNA)",
                    license = "Apache License 2.0 / LGPL 2.1",
                    copyright = "Timothy Wall and JNA contributors",
                    description = "Provides native OS access for secure desktop keystore integration on Linux and Windows."
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                NoticeItem(
                    title = "dbus-java Transport",
                    license = "LGPL 2.1 / Academic Free License 2.1",
                    copyright = "Hypfvieh / Matthew Johnson",
                    description = "Native Unix socket D-Bus transport for Linux Secret Service vault integration."
                )

                Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingSmall))

                NoticeItem(
                    title = "Typography System Font Architecture",
                    license = "Platform System Sans-Serif Fallback",
                    copyright = "Visual styling inspired by ForumFlash light palette; renders via native system typography.",
                    description = "In accordance with Phase 06 design specifications, binary font downloads are omitted in favor of accessible native platform system fonts (Roboto on Android, Inter/Cantarell on Linux, Segoe UI on Windows)."
                )
            }

            Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingLarge))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TabbyRushThemeTokens.MinTouchTargetSize),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun NoticeItem(
    title: String,
    license: String,
    copyright: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = TabbyRushThemeTokens.customColors.mintSurface,
                shape = MaterialTheme.shapes.small
            )
            .padding(TabbyRushThemeTokens.SpacingMedium)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = license,
            style = MaterialTheme.typography.labelMedium,
            color = TabbyRushThemeTokens.customColors.primaryDeep
        )
        Text(
            text = copyright,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(TabbyRushThemeTokens.SpacingXSmall))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TabbyRushThemeTokens.customColors.muted
        )
    }
}
