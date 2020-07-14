/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.integration.test.foundation

import androidx.compose.Composable
import androidx.compose.key
import androidx.compose.remember
import androidx.ui.core.Alignment
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.foundation.ScrollState
import androidx.ui.foundation.ScrollableColumn
import androidx.ui.foundation.ScrollableRow
import androidx.ui.foundation.Text
import androidx.ui.foundation.drawBackground
import androidx.ui.foundation.rememberScrollState
import androidx.ui.graphics.Color
import androidx.ui.integration.test.ToggleableTestCase
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.layout.RowScope
import androidx.ui.layout.fillMaxHeight
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.preferredSize
import androidx.ui.material.MaterialTheme
import androidx.ui.material.Surface
import androidx.ui.test.ComposeTestCase
import kotlin.random.Random

/**
 * Test case that puts many horizontal scrollers in a vertical scroller
 */
class NestedScrollerTestCase : ComposeTestCase, ToggleableTestCase {
    // ScrollerPosition must now be constructed during composition to obtain the Density
    private lateinit var scrollState: ScrollState

    @Composable
    override fun emitContent() {
        scrollState = rememberScrollState()
        MaterialTheme {
            Surface {
                ScrollableColumn {
                    repeat(5) { index ->
                        // key is needed because of b/154920561
                        key(index) {
                            SquareRow(index == 0)
                        }
                    }
                }
            }
        }
    }

    override fun toggleState() {
        scrollState.scrollTo(if (scrollState.value == 0f) 10f else 0f)
    }

    @Composable
    fun SquareRow(useScrollerPosition: Boolean) {
        val playStoreColor = Color(red = 0x00, green = 0x00, blue = 0x80)
        val content: @Composable RowScope.() -> Unit = {
            repeat(6) {
                with(DensityAmbient.current) {
                    Column(Modifier.fillMaxHeight()) {
                        val color = remember {
                            val red = Random.nextInt(256)
                            val green = Random.nextInt(256)
                            val blue = Random.nextInt(256)
                            Color(red = red, green = green, blue = blue)
                        }
                        Box(Modifier.preferredSize(350f.toDp()).drawBackground(color))
                        Text(
                            text = "Some title",
                            color = Color.Black,
                            fontSize = 60f.toSp()
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "3.5 ★",
                                fontSize = 40.toSp(),
                                modifier = Modifier.gravity(Alignment.CenterVertically)
                            )
                            Box(
                                Modifier
                                    .gravity(Alignment.CenterVertically)
                                    .preferredSize(40f.toDp())
                                    .drawBackground(playStoreColor)
                            )
                        }
                    }
                }
            }
        }
        if (useScrollerPosition) {
            ScrollableRow(scrollState = scrollState, children = content)
        } else {
            ScrollableRow(children = content)
        }
    }
}