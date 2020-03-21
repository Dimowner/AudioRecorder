/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder;

public class IntArrayList {

	private int[] data = new int[100];
	private int size = 0;

	public void add(int val) {
		if (data.length == size) {
			grow();
			add(val);
		}
		data[size] = val;
		size++;
	}

	public int get(int index) {
		return data[index];
	}

	public int[] getData() {
		int [] arr = new int[size];
		for (int i = 0; i < size; i++) {
			arr[i] = data[i];
		}
		return arr;
	}

	public void clear() {
		data = new int[100];
		size = 0;
	}

	public int size() {
		return size;
	}

	private void grow() {
		int[] backup = data;
		data = new int[size*2];
		for (int i = 0; i < backup.length; i++) {
			data[i] = backup[i];
		}
	}
}
