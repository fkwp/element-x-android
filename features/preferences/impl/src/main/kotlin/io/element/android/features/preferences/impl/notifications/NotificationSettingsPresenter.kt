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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.notificationsettings.NotificationSettingsService
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.push.api.PushService
import io.element.android.libraries.pushproviders.api.Distributor
import io.element.android.libraries.pushproviders.api.PushProvider
import io.element.android.libraries.pushstore.api.UserPushStore
import io.element.android.libraries.pushstore.api.UserPushStoreFactory
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class NotificationSettingsPresenter @Inject constructor(
    private val notificationSettingsService: NotificationSettingsService,
    private val userPushStoreFactory: UserPushStoreFactory,
    private val matrixClient: MatrixClient,
    private val pushService: PushService,
    private val systemNotificationsEnabledProvider: SystemNotificationsEnabledProvider,
) : Presenter<NotificationSettingsState> {
    @Composable
    override fun present(): NotificationSettingsState {
        val userPushStore = remember { userPushStoreFactory.getOrCreate(matrixClient.sessionId) }
        val systemNotificationsEnabled: MutableState<Boolean> = remember {
            mutableStateOf(systemNotificationsEnabledProvider.notificationsEnabled())
        }
        val changeNotificationSettingAction: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }

        val localCoroutineScope = rememberCoroutineScope()
        val appNotificationsEnabled = userPushStore
            .getNotificationEnabledForDevice()
            .collectAsState(initial = false)

        val matrixSettings: MutableState<NotificationSettingsState.MatrixSettings> = remember {
            mutableStateOf(NotificationSettingsState.MatrixSettings.Uninitialized)
        }

        LaunchedEffect(Unit) {
            fetchSettings(matrixSettings)
            observeNotificationSettings(matrixSettings)
        }

        // List of PushProvider -> Distributor
        val distributors = remember {
            pushService.getAvailablePushProviders()
                .flatMap { pushProvider ->
                    pushProvider.getDistributors().map { distributor ->
                        pushProvider to distributor
                    }
                }
        }
        // List of Distributor names
        val distributorNames = remember {
            distributors.map { it.second.name }
        }

        var currentDistributorName by remember { mutableStateOf<AsyncAction<String>>(AsyncAction.Uninitialized) }
        var refreshPushProvider by remember { mutableIntStateOf(0) }

        LaunchedEffect(refreshPushProvider) {
            val p = pushService.getCurrentPushProvider()
            val name = p?.getCurrentDistributor(matrixClient)?.name
            currentDistributorName = if (name != null) {
                AsyncAction.Success(name)
            } else {
                AsyncAction.Failure(Exception("Failed to get current push provider"))
            }
        }

        var showChangePushProviderDialog by remember { mutableStateOf(false) }

        fun CoroutineScope.changePushProvider(
            data: Pair<PushProvider, Distributor>?
        ) = launch {
            showChangePushProviderDialog = false
            data ?: return@launch
            // No op if the value is the same.
            if (data.second.name == currentDistributorName.dataOrNull()) return@launch
            currentDistributorName = AsyncAction.Loading
            data.let { (pushProvider, distributor) ->
                pushService.registerWith(
                    matrixClient = matrixClient,
                    pushProvider = pushProvider,
                    distributor = distributor
                )
                    .fold(
                        {
                            currentDistributorName = AsyncAction.Success(distributor.name)
                            refreshPushProvider++
                        },
                        {
                            currentDistributorName = AsyncAction.Failure(it)
                        }
                    )
            }
        }

        fun handleEvents(event: NotificationSettingsEvents) {
            when (event) {
                is NotificationSettingsEvents.SetAtRoomNotificationsEnabled -> {
                    localCoroutineScope.setAtRoomNotificationsEnabled(event.enabled, changeNotificationSettingAction)
                }
                is NotificationSettingsEvents.SetCallNotificationsEnabled -> {
                    localCoroutineScope.setCallNotificationsEnabled(event.enabled, changeNotificationSettingAction)
                }
                is NotificationSettingsEvents.SetInviteForMeNotificationsEnabled -> {
                    localCoroutineScope.setInviteForMeNotificationsEnabled(event.enabled, changeNotificationSettingAction)
                }
                is NotificationSettingsEvents.SetNotificationsEnabled -> localCoroutineScope.setNotificationsEnabled(userPushStore, event.enabled)
                NotificationSettingsEvents.ClearConfigurationMismatchError -> {
                    matrixSettings.value = NotificationSettingsState.MatrixSettings.Invalid(fixFailed = false)
                }
                NotificationSettingsEvents.FixConfigurationMismatch -> localCoroutineScope.fixConfigurationMismatch(matrixSettings)
                NotificationSettingsEvents.RefreshSystemNotificationsEnabled -> {
                    systemNotificationsEnabled.value = systemNotificationsEnabledProvider.notificationsEnabled()
                }
                NotificationSettingsEvents.ClearNotificationChangeError -> changeNotificationSettingAction.value = AsyncAction.Uninitialized
                NotificationSettingsEvents.ChangePushProvider -> showChangePushProviderDialog = true
                NotificationSettingsEvents.CancelChangePushProvider -> showChangePushProviderDialog = false
                is NotificationSettingsEvents.SetPushProvider -> localCoroutineScope.changePushProvider(distributors.getOrNull(event.index))
            }
        }

        return NotificationSettingsState(
            matrixSettings = matrixSettings.value,
            appSettings = NotificationSettingsState.AppSettings(
                systemNotificationsEnabled = systemNotificationsEnabled.value,
                appNotificationsEnabled = appNotificationsEnabled.value
            ),
            changeNotificationSettingAction = changeNotificationSettingAction.value,
            currentPushDistributor = currentDistributorName,
            availablePushDistributors = distributorNames.toImmutableList(),
            showChangePushProviderDialog = showChangePushProviderDialog,
            eventSink = ::handleEvents
        )
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.observeNotificationSettings(target: MutableState<NotificationSettingsState.MatrixSettings>) {
        notificationSettingsService.notificationSettingsChangeFlow
            .debounce(0.5.seconds)
            .onEach {
                fetchSettings(target)
            }
            .launchIn(this)
    }

    private fun CoroutineScope.fetchSettings(target: MutableState<NotificationSettingsState.MatrixSettings>) = launch {
        val groupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = false).getOrThrow()
        val encryptedGroupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = false).getOrThrow()

        val oneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = true).getOrThrow()
        val encryptedOneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = true).getOrThrow()

        if (groupDefaultMode != encryptedGroupDefaultMode || oneToOneDefaultMode != encryptedOneToOneDefaultMode) {
            target.value = NotificationSettingsState.MatrixSettings.Invalid(fixFailed = false)
            return@launch
        }

        val callNotificationsEnabled = notificationSettingsService.isCallEnabled().getOrThrow()
        val atRoomNotificationsEnabled = notificationSettingsService.isRoomMentionEnabled().getOrThrow()
        val inviteForMeNotificationsEnabled = notificationSettingsService.isInviteForMeEnabled().getOrThrow()

        target.value = NotificationSettingsState.MatrixSettings.Valid(
            atRoomNotificationsEnabled = atRoomNotificationsEnabled,
            callNotificationsEnabled = callNotificationsEnabled,
            inviteForMeNotificationsEnabled = inviteForMeNotificationsEnabled,
            defaultGroupNotificationMode = encryptedGroupDefaultMode,
            defaultOneToOneNotificationMode = encryptedOneToOneDefaultMode,
        )
    }

    private fun CoroutineScope.fixConfigurationMismatch(target: MutableState<NotificationSettingsState.MatrixSettings>) = launch {
        runCatching {
            val groupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = false).getOrThrow()
            val encryptedGroupDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = false).getOrThrow()

            if (groupDefaultMode != encryptedGroupDefaultMode) {
                notificationSettingsService.setDefaultRoomNotificationMode(
                    isEncrypted = encryptedGroupDefaultMode != RoomNotificationMode.ALL_MESSAGES,
                    mode = RoomNotificationMode.ALL_MESSAGES,
                    isOneToOne = false,
                )
            }

            val oneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = false, isOneToOne = true).getOrThrow()
            val encryptedOneToOneDefaultMode = notificationSettingsService.getDefaultRoomNotificationMode(isEncrypted = true, isOneToOne = true).getOrThrow()

            if (oneToOneDefaultMode != encryptedOneToOneDefaultMode) {
                notificationSettingsService.setDefaultRoomNotificationMode(
                    isEncrypted = encryptedOneToOneDefaultMode != RoomNotificationMode.ALL_MESSAGES,
                    mode = RoomNotificationMode.ALL_MESSAGES,
                    isOneToOne = true,
                )
            }
        }.fold(
            onSuccess = {},
            onFailure = {
                target.value = NotificationSettingsState.MatrixSettings.Invalid(fixFailed = true)
            }
        )
    }

    private fun CoroutineScope.setAtRoomNotificationsEnabled(enabled: Boolean, action: MutableState<AsyncAction<Unit>>) = launch {
        suspend {
            notificationSettingsService.setRoomMentionEnabled(enabled).getOrThrow()
        }.runCatchingUpdatingState(action)
    }

    private fun CoroutineScope.setCallNotificationsEnabled(enabled: Boolean, action: MutableState<AsyncAction<Unit>>) = launch {
        suspend {
            notificationSettingsService.setCallEnabled(enabled).getOrThrow()
        }.runCatchingUpdatingState(action)
    }

    private fun CoroutineScope.setInviteForMeNotificationsEnabled(enabled: Boolean, action: MutableState<AsyncAction<Unit>>) = launch {
        suspend {
            notificationSettingsService.setInviteForMeEnabled(enabled).getOrThrow()
        }.runCatchingUpdatingState(action)
    }

    private fun CoroutineScope.setNotificationsEnabled(userPushStore: UserPushStore, enabled: Boolean) = launch {
        userPushStore.setNotificationEnabledForDevice(enabled)
    }
}
