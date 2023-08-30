/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.libraries.featureflag.api

enum class FeatureFlags(
    override val key: String,
    override val title: String,
    override val description: String? = null,
    override val defaultValue: Boolean = true
) : Feature {
    LocationSharing(
        key = "feature.locationsharing",
        title = "Allow user to share location",
    ),
    Polls(
        key = "feature.polls",
        title = "Polls",
        description = "Create poll and render poll events in the timeline",
        defaultValue = false,
    ),
    NotificationSettings(
        key = "feature.notificationsettings",
        title = "Show notification settings",
    )
}
