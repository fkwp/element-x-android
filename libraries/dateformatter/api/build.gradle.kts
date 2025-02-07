/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.dateformatter.api"

    dependencies {
        testImplementation(libs.test.junit)
        testImplementation(libs.test.truth)
    }
}
