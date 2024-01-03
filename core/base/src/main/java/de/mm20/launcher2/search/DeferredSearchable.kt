package de.mm20.launcher2.search

import kotlinx.coroutines.Deferred

/**
 * Interface that can be implemented by [SavableSearchable]s to provide a way to update itself.
 * Consumers of [SavableSearchable]s can check if the [SavableSearchable] implements this interface
 * and decide to get an updated version of the [SavableSearchable] by calling [updatedSelf], which
 * returns a [Deferred] that will resolve to the updated [SavableSearchable].
 */
interface DeferredSearchable<T: SavableSearchable> {
    val updatedSelf: Deferred<T?>?
}