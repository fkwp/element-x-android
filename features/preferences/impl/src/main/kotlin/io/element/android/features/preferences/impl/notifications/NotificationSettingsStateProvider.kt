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

package io.element.android.features.preferences.impl.notifications

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class NotificationSettingsStateProvider : PreviewParameterProvider<NotificationSettingsState> {
    override val values: Sequence<NotificationSettingsState>
        get() = sequenceOf(
            aValidNotificationSettingsState(),
            aValidNotificationSettingsState(systemNotificationsEnabled = false),
            aValidNotificationSettingsState(changeNotificationSettingAction = AsyncAction.Loading),
            aValidNotificationSettingsState(changeNotificationSettingAction = AsyncAction.Failure(Throwable("error"))),
            aValidNotificationSettingsState(showChangePushProviderDialog = true),
            aValidNotificationSettingsState(currentPushDistributor = AsyncAction.Loading),
            aValidNotificationSettingsState(currentPushDistributor = AsyncAction.Failure(Exception("Failed to change distributor"))),
            aInvalidNotificationSettingsState(),
            aInvalidNotificationSettingsState(fixFailed = true),
        )
}

fun aValidNotificationSettingsState(
    changeNotificationSettingAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    atRoomNotificationsEnabled: Boolean = true,
    callNotificationsEnabled: Boolean = true,
    inviteForMeNotificationsEnabled: Boolean = true,
    systemNotificationsEnabled: Boolean = true,
    appNotificationEnabled: Boolean = true,
    currentPushDistributor: AsyncAction<String> = AsyncAction.Success("Firebase"),
    availablePushDistributors: List<String> = listOf("Firebase", "ntfy"),
    showChangePushProviderDialog: Boolean = false,
    eventSink: (NotificationSettingsEvents) -> Unit = {},
) = NotificationSettingsState(
    matrixSettings = NotificationSettingsState.MatrixSettings.Valid(
        atRoomNotificationsEnabled = atRoomNotificationsEnabled,
        callNotificationsEnabled = callNotificationsEnabled,
        inviteForMeNotificationsEnabled = inviteForMeNotificationsEnabled,
        defaultGroupNotificationMode = RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY,
        defaultOneToOneNotificationMode = RoomNotificationMode.ALL_MESSAGES,
    ),
    appSettings = NotificationSettingsState.AppSettings(
        systemNotificationsEnabled = systemNotificationsEnabled,
        appNotificationsEnabled = appNotificationEnabled,
    ),
    changeNotificationSettingAction = changeNotificationSettingAction,
    currentPushDistributor = currentPushDistributor,
    availablePushDistributors = availablePushDistributors.toImmutableList(),
    showChangePushProviderDialog = showChangePushProviderDialog,
    eventSink = eventSink,
)

fun aInvalidNotificationSettingsState(
    fixFailed: Boolean = false,
    eventSink: (NotificationSettingsEvents) -> Unit = {},
) = NotificationSettingsState(
    matrixSettings = NotificationSettingsState.MatrixSettings.Invalid(
        fixFailed = fixFailed,
    ),
    appSettings = NotificationSettingsState.AppSettings(
        systemNotificationsEnabled = false,
        appNotificationsEnabled = true,
    ),
    changeNotificationSettingAction = AsyncAction.Uninitialized,
    currentPushDistributor = AsyncAction.Uninitialized,
    availablePushDistributors = persistentListOf(),
    showChangePushProviderDialog = false,
    eventSink = eventSink,
)
