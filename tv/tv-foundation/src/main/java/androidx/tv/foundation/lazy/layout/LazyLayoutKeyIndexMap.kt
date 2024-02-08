/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.tv.foundation.lazy.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.getDefaultLazyLayoutKey

/**
 * A key-index mapping used inside the [LazyLayoutItemProvider]. It might not contain all items
 * in the lazy layout as optimization, but it must cover items the provider is requesting
 * during layout pass.
 * See [NearestRangeKeyIndexMap] as sample implementation that samples items near current viewport.
 */
internal interface LazyLayoutKeyIndexMap {
    /**
     * @return current index for given [key] or `-1` if not found.
     */
    fun getIndex(key: Any): Int

    /**
     * @return key for a given [index] if it is known, or null otherwise.
     */
    fun getKey(index: Int): Any?

    /**
     * Empty map implementation, always returning `-1` for any key.
     */
    companion object Empty : LazyLayoutKeyIndexMap {
        @Suppress("AutoBoxing")
        override fun getIndex(key: Any): Int = -1
        override fun getKey(index: Int) = null
    }
}

/**
 * Implementation of [LazyLayoutKeyIndexMap] indexing over given [IntRange] of items.
 * Items outside of given range are considered unknown, with null returned as the index.
 */
@Suppress("IllegalExperimentalApiUsage") // TODO (b/233188423): Address before moving to beta
@ExperimentalFoundationApi
internal class NearestRangeKeyIndexMap(
    nearestRange: IntRange,
    intervalContent: LazyLayoutIntervalContent<*>
) : LazyLayoutKeyIndexMap {
    private val map: Map<Any, Int>
    private val keys: Array<Any?>
    private val keysStartIndex: Int

    init {
        // Traverses the interval [list] in order to create a mapping from the key to the index for
        // all the indexes in the passed [range].
        val list = intervalContent.intervals
        val first = nearestRange.first
        check(first >= 0) { "negative nearestRange.first" }
        val last = minOf(nearestRange.last, list.size - 1)
        if (last < first) {
            map = emptyMap()
            keys = emptyArray()
            keysStartIndex = 0
        } else {
            keys = arrayOfNulls<Any?>(last - first + 1)
            keysStartIndex = first
            map = hashMapOf<Any, Int>().also { map ->
                list.forEach(
                    fromIndex = first,
                    toIndex = last,
                ) {
                    val keyFactory = it.value.key
                    val start = maxOf(first, it.startIndex)
                    val end = minOf(last, it.startIndex + it.size - 1)
                    for (i in start..end) {
                        val key =
                            keyFactory?.invoke(i - it.startIndex) ?: getDefaultLazyLayoutKey(i)
                        map[key] = i
                        keys[i - keysStartIndex] = key
                    }
                }
            }
        }
    }

    override fun getIndex(key: Any): Int = map.getOrElse(key) { -1 }

    override fun getKey(index: Int) =
        keys.getOrElse(index - keysStartIndex) { null }
}

/**
 * Returns a range of indexes which contains at least [extraItemCount] items near
 * the first visible item. It is optimized to return the same range for small changes in the
 * firstVisibleItem value so we do not regenerate the map on each scroll.
 */
private fun calculateNearestItemsRange(
    firstVisibleItem: Int,
    slidingWindowSize: Int,
    extraItemCount: Int
): IntRange {
    val slidingWindowStart = slidingWindowSize * (firstVisibleItem / slidingWindowSize)

    val start = maxOf(slidingWindowStart - extraItemCount, 0)
    val end = slidingWindowStart + slidingWindowSize + extraItemCount
    return start until end
}