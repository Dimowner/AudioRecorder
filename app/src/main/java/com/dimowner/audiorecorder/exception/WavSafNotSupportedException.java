/*
 * Copyright 2024 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.exception;

/**
 * Exception thrown when attempting to record WAV format to SD card via SAF.
 * WAV recording requires RandomAccessFile to update the header after recording,
 * which is not supported by the Storage Access Framework.
 */
public class WavSafNotSupportedException extends AppException {
	@Override
	public int getType() {
		return AppException.WAV_FORMAT_NOT_SUPPORTED_SAF;
	}
}
