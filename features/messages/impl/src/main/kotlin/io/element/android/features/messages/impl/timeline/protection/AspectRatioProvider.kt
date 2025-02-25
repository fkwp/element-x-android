/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.protection

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class AspectRatioProvider : PreviewParameterProvider<Float?> {
    override val values: Sequence<Float?> = sequenceOf(
        null,
        0.05f,
        1f,
        20f,
    )
}
