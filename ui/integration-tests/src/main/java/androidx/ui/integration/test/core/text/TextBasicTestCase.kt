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

package androidx.ui.integration.test.core.text

import androidx.compose.Composable
import androidx.compose.mutableStateOf
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.graphics.Color
import androidx.ui.integration.test.ToggleableTestCase
import androidx.ui.layout.preferredWidth
import androidx.ui.layout.wrapContentSize
import androidx.ui.test.ComposeTestCase
import androidx.ui.unit.Dp
import androidx.ui.unit.TextUnit

/**
 * The benchmark test case for [Text], where the input is a plain string.
 */
class TextBasicTestCase(
    private val text: String,
    private val width: Dp,
    private val fontSize: TextUnit
) : ComposeTestCase, ToggleableTestCase {

    private val color = mutableStateOf(Color.Black)

    @Composable
    override fun emitContent() {
        Box(
            modifier = Modifier.wrapContentSize(Alignment.Center).preferredWidth(width)
        ) {
            Text(text = text, color = color.value, fontSize = fontSize)
        }
    }

    override fun toggleState() {
        if (color.value == Color.Black) {
            color.value = Color.Red
        } else {
            color.value = Color.Black
        }
    }
}