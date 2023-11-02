package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CloudBadgeProvider: BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> = flow {
        if (searchable is File) {
            val iconResId = searchable.providerIconRes
            if (iconResId != null) {
                emit(Badge(iconRes = iconResId))
                return@flow
            }
        }
        emit(null)
    }
}