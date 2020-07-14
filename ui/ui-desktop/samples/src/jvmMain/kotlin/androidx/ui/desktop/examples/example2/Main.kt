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
package androidx.ui.desktop.examples.example2

import androidx.ui.core.Modifier
import androidx.ui.foundation.Canvas
import androidx.ui.geometry.Offset
import androidx.ui.graphics.Color
import androidx.ui.graphics.drawscope.inset
import androidx.ui.graphics.drawscope.rotate
import androidx.ui.graphics.drawscope.withTransform
import androidx.ui.layout.fillMaxSize
import androidx.ui.unit.IntSize
import androidx.ui.desktop.AppWindow

private const val title = "Desktop Compose Canvas"

fun main() {
    AppWindow(title, IntSize(1024, 768)).show {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Magenta)
            inset(10.0f) {
                drawLine(
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                    strokeWidth = 5.0f,
                    color = Color.Red
                )
            }
            floatArrayOf(0.3f, 0.7f, 1.3f).forEach {
                withTransform({
                    translate(10.0f, 12.0f)
                    rotate(45f * it)
                    scale(it, 1f / it)
                }) {
                    drawRect(Color(0f, 0f, 1f, it / 2f))
                    drawCircle(Color(1f, 0f, 0f, it / 2f))
                }
            }
        }
    }
}