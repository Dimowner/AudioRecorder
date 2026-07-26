/*
 * Copyright 2026 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.app.nameformat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.NameFormatToken
import com.dimowner.audiorecorder.v2.data.model.NameFormatTokenType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exercises [NameFormatConstructorViewModel]'s drag-and-drop reordering of the constructed tokens. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class NameFormatConstructorViewModelTest {

    private lateinit var prefs: PrefsV2

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
    }

    private fun viewModelWithTokens(vararg types: NameFormatTokenType): NameFormatConstructorViewModel {
        val viewModel = NameFormatConstructorViewModel(prefs)
        types.forEach {
            viewModel.onAction(NameFormatConstructorAction.AddToken(NameFormatToken(it)))
        }
        return viewModel
    }

    private fun NameFormatConstructorViewModel.tokenTypes() = state.value.tokens.map { it.type }

    @Test
    fun `moving a token forward reorders it into the target slot`() {
        val viewModel = viewModelWithTokens(
            NameFormatTokenType.Year,
            NameFormatTokenType.Month,
            NameFormatTokenType.Day,
        )

        viewModel.onAction(NameFormatConstructorAction.MoveToken(fromIndex = 0, toIndex = 2))

        assertEquals(
            listOf(NameFormatTokenType.Month, NameFormatTokenType.Day, NameFormatTokenType.Year),
            viewModel.tokenTypes(),
        )
    }

    @Test
    fun `moving a token backward reorders it into the target slot`() {
        val viewModel = viewModelWithTokens(
            NameFormatTokenType.Year,
            NameFormatTokenType.Month,
            NameFormatTokenType.Day,
        )

        viewModel.onAction(NameFormatConstructorAction.MoveToken(fromIndex = 2, toIndex = 0))

        assertEquals(
            listOf(NameFormatTokenType.Day, NameFormatTokenType.Year, NameFormatTokenType.Month),
            viewModel.tokenTypes(),
        )
    }

    @Test
    fun `moving to the same index leaves order unchanged`() {
        val viewModel = viewModelWithTokens(
            NameFormatTokenType.Year,
            NameFormatTokenType.Month,
        )

        viewModel.onAction(NameFormatConstructorAction.MoveToken(fromIndex = 1, toIndex = 1))

        assertEquals(
            listOf(NameFormatTokenType.Year, NameFormatTokenType.Month),
            viewModel.tokenTypes(),
        )
    }

    @Test
    fun `moving with an out of bounds index is ignored`() {
        val viewModel = viewModelWithTokens(
            NameFormatTokenType.Year,
            NameFormatTokenType.Month,
        )

        viewModel.onAction(NameFormatConstructorAction.MoveToken(fromIndex = 0, toIndex = 5))

        assertEquals(
            listOf(NameFormatTokenType.Year, NameFormatTokenType.Month),
            viewModel.tokenTypes(),
        )
    }
}
