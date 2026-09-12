/*
 * Copyright 2018 Dmytro Ponomarenko
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

	private static final int INITIAL_CAPACITY = 100;

	private int[] data = new int[INITIAL_CAPACITY];
	private int size = 0;

	public void add(int val) {
		if (data.length == size) {
			grow();
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
		// Keep the backing array so the recorders, which clear this buffer on every progress
		// tick (50 times a second), don't allocate a fresh array each time - over a multi-hour
		// recording that alone is hundreds of thousands of throwaway arrays.
		// Only an array that grew unusually large is released.
		if (data.length > INITIAL_CAPACITY * 16) {
			data = new int[INITIAL_CAPACITY];
		}
		size = 0;
	}

	public int size() {
		return size;
	}

	private void grow() {
		int[] backup = data;
		data = new int[data.length * 2];
		System.arraycopy(backup, 0, data, 0, backup.length);
	}
}
