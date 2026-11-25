// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/**
 * Stands in for the activity behind a system picker in screen tests. Provide it through
 * `LocalActivityResultRegistryOwner`: each launch is recorded in [requestCodes] instead of opening
 * the picker, and the test answers with `activityResultRegistry.dispatchResult(requestCode, …)`.
 */
internal class RecordingPickerOwner : ActivityResultRegistryOwner {
  val requestCodes = mutableListOf<Int>()
  override val activityResultRegistry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
          requestCodes.add(requestCode)
        }
      }
}
