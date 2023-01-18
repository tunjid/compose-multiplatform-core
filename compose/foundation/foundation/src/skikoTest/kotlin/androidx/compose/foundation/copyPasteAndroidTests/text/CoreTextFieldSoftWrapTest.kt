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

package androidx.compose.foundation.copyPasteAndroidTests.text

import androidx.compose.foundation.assertThat
import androidx.compose.foundation.isEqualTo
import androidx.compose.foundation.isNotNull
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.CoreTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CoreTextFieldSoftWrapTest {
    
    @Test
    fun textField_softWrapFalse_returnsSizeForMaxIntrinsicWidth() = runSkikoComposeUiTest {
        val density = Density(density = 1f, fontScale = 1f)
        val fontSize = 100.sp
        val composableWidth = 50.dp
        val textStyle = TextStyle(
            fontFamily = FontFamily.Default, 
            fontSize = fontSize
        )
        val string = "a".repeat(20)

        var textLayout: TextLayoutResult? = null
        var width: Int? = null

        setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                CoreTextField(
                    value = TextFieldValue(string),
                    onValueChange = {},
                    textStyle = textStyle,
                    softWrap = false,
                    onTextLayout = { textLayout = it },
                    modifier = Modifier.requiredWidth(composableWidth)
                        .onGloballyPositioned {
                            width = it.size.width
                        }
                )
            }
        }

        waitUntil { width != null }

        with(density) {
            assertThat(textLayout).isNotNull()
            assertThat(width).isNotNull()
            assertThat(width).isEqualTo(composableWidth.roundToPx())
            assertThat(textLayout?.lineCount).isEqualTo(1)
        }
    }

    @Test
    fun textField_softWrapTrue_respectsTheGivenMaxWidth() = runSkikoComposeUiTest {
        val density = Density(density = 1f, fontScale = 1f)
        val fontSize = 100.sp
        val composableWidth = 100.dp
        val textStyle = TextStyle(
            fontFamily = FontFamily.Default, 
            fontSize = fontSize
        )
        val string = "a".repeat(20)

        var textLayout: TextLayoutResult? = null
        var width: Int? = null

        setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                CoreTextField(
                    value = TextFieldValue(string),
                    onValueChange = {},
                    textStyle = textStyle,
                    softWrap = true,
                    onTextLayout = { textLayout = it },
                    modifier = Modifier.requiredWidth(composableWidth)
                        .onGloballyPositioned {
                            width = it.size.width
                        }
                )
            }
        }

        waitUntil { width != null }

        with(density) {
            assertThat(textLayout).isNotNull()
            assertThat(width).isNotNull()
            assertThat(width).isEqualTo(composableWidth.roundToPx())
            // each character has the same width as composable width
            // therefore the string.length is the line count
            assertThat(textLayout?.lineCount).isEqualTo(string.length)
        }
    }
}