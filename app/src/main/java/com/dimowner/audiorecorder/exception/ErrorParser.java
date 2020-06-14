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

import com.dimowner.audiorecorder.R;

public class ErrorParser {

	private ErrorParser() {}

	public static int parseException(AppException e) {
		if (e.getType() == AppException.CANT_CREATE_FILE) {
			return R.string.error_cant_create_file;
		} else if (e.getType() == AppException.INVALID_OUTPUT_FILE) {
			return R.string.error_invalid_output_file;
		} else if (e.getType() == AppException.RECORDER_INIT_EXCEPTION) {
			return R.string.error_failed_to_init_recorder;
		} else if (e.getType() == AppException.PLAYER_DATA_SOURCE_EXCEPTION) {
			return R.string.error_player_data_source;
		} else if (e.getType() == AppException.PLAYER_INIT_EXCEPTION) {
			return R.string.error_failed_to_init_player;
		} else if (e.getType() == AppException.CANT_PROCESS_RECORD) {
			return R.string.error_process_waveform;
		} else if (e.getType() == AppException.NO_SPACE_AVAILABLE) {
			return R.string.error_no_available_space;
		} else if (e.getType() == AppException.RECORDING_ERROR) {
			return R.string.error_on_recording;
		} else if (e.getType() == AppException.FAILED_TO_RESTORE) {
			return R.string.error_failed_to_restore;
		} else if (e.getType() == AppException.READ_PERMISSION_DENIED) {
			return R.string.error_permission_denied;
		}
		return R.string.error_unknown;
	}
}
