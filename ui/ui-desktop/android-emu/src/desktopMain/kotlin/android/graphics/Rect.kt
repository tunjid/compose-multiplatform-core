/*
 * Copyright 2020 The Android Open Source Project
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

package android.graphics

import org.jetbrains.skija.Rect as SkRect

class Rect(
    @kotlin.jvm.JvmField
    var left: Int,
    @kotlin.jvm.JvmField
    var top: Int,
    @kotlin.jvm.JvmField
    var right: Int,
    @kotlin.jvm.JvmField
    var bottom: Int
) {
    constructor() : this(0, 0, 0, 0)

    fun set(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }
}

internal fun Rect.toSkia() =
    SkRect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())