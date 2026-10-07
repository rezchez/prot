/*
 * Copyright (c) 2026. Proton AG
 *
 *  This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.bugreport

import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.SharedStoreProvider
import com.protonvpn.android.userstorage.StoreProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugReportConfigStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<DynamicReportModel>(
    "default_bug_report",
    DefaultBugReport,
    DynamicReportModel.serializer(),
    factory,
)

@Singleton
class BugReportConfigStore @Inject constructor(
    bugReportConfigStore: BugReportConfigStoreProvider,
) {
    private val dataStore = SharedStoreProvider(bugReportConfigStore)

    suspend fun load(): DynamicReportModel =
        dataStore.sharedDataFlow().first()

    suspend fun save(model: DynamicReportModel) {
        dataStore.sharedUpdate { model }
    }
}