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

package com.dimowner.audiorecorder.exception;

public abstract class AppException extends Exception {

	public static final int CANT_CREATE_FILE = 1;
	public static final int INVALID_OUTPUT_FILE = 2;
	public static final int RECORDER_INIT_EXCEPTION = 3;
	public static final int PLAYER_INIT_EXCEPTION = 4;
	public static final int PLAYER_DATA_SOURCE_EXCEPTION = 5;
	public static final int CANT_PROCESS_RECORD = 6;
	public static final int READ_PERMISSION_DENIED = 7;
	public static final int NO_SPACE_AVAILABLE = 8;
	public static final int RECORDING_ERROR = 9;
	public static final int FAILED_TO_RESTORE = 10;

	public abstract int getType();
}
