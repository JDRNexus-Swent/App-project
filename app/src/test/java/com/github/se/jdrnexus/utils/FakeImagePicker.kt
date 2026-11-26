// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat

/**
 * Stands in for the Android photo picker in Compose tests: every launch is recorded in [requests]
 * and answered at once with [picked] (null = the user closed the picker). Wrap the tested content
 * in [Provide].
 */
class FakeImagePicker(var picked: Uri? = null) : ActivityResultRegistryOwner {
  /** The requests of every launch, in order. */
  val requests = mutableListOf<PickVisualMediaRequest>()

  override val activityResultRegistry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
          requests += input as PickVisualMediaRequest
          dispatchResult(requestCode, picked)
        }
      }

  /** Shows [content] with this picker as the one any `rememberLauncherForActivityResult` uses. */
  @Composable
  fun Provide(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides this, content = content)
  }
}
