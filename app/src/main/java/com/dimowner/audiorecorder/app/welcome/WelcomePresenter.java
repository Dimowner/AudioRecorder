/*
 * Copyright 2020 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.welcome;

/**
 * Created on 04.04.2020.
 * @author Dimowner
 */
public class WelcomePresenter implements WelcomeContract.UserActionsListener {

	private WelcomeContract.View view;

	public WelcomePresenter() {
	}

	@Override
	public void bindView(final WelcomeContract.View v) {
		this.view = v;
	}

	@Override
	public void unbindView() {
		this.view = null;
	}

	@Override
	public void clear() {
		unbindView();
	}
}
