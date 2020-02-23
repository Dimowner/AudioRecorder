/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder;

/**
 * AppConstants that may be used in multiple classes.
 */
public class AppConstants {

	private AppConstants() {}

	public static final String REQUESTS_RECEIVER = "dimmony@gmail.com";

	public static final String APPLICATION_NAME = "AudioRecorder";
	public static final String RECORDS_DIR = "records";
	public static final String M4A_EXTENSION = "m4a";
	public static final String WAV_EXTENSION = "wav";
	public static final String EXTENSION_SEPARATOR = ".";
	public static final String BASE_RECORD_NAME = "Record-";
	public static final String BASE_RECORD_NAME_SHORT = "Rec-";
	public static final String TRASH_MARK_EXTENSION = "del";
	public static final int MAX_RECORD_NAME_LENGTH = 50;

	public static final int NAMING_COUNTED = 0;
	public static final int NAMING_DATE = 1;

	public static final int RECORDING_FORMAT_M4A = 0;
	public static final int RECORDING_FORMAT_WAV = 1;

	public static final int DEFAULT_PER_PAGE = 50;

	public final static long RECORD_IN_TRASH_MAX_DURATION = 5184000000L; // 1000 X 60 X 60 X 24 X 60 = 60 Days
	public final static long MIN_REMAIN_RECORDING_TIME = 60000; // 1000 X 60 = 1 Minute

	//BEGINNING-------------- Waveform visualisation constants ----------------------------------

	/** Density pixel count per one second of time.
	 *  Used for short records (shorter than {@link AppConstants#LONG_RECORD_THRESHOLD_SECONDS}) */
	public static final int SHORT_RECORD_DP_PER_SECOND = 25;

	/** Waveform length, measured in screens count of device.
	 *  Used for long records (longer than {@link AppConstants#LONG_RECORD_THRESHOLD_SECONDS})   */
	public static final float WAVEFORM_WIDTH = 1.5f; //one and half of screen waveform width.

	/** Threshold in second which defines when record is considered as long or short.
	 *  For short and long records used a bit different visualisation algorithm. */
	public static final int LONG_RECORD_THRESHOLD_SECONDS = 20;

	/** Count of grid lines on visible part of Waveform (actually lines count visible on screen).
	 *  Used for long records visualisation algorithm. (longer than {@link AppConstants#LONG_RECORD_THRESHOLD_SECONDS} ) */
	public static final int GRID_LINES_COUNT = 16;

	//END-------------- Waveform visualisation constants ----------------------------------------

	public static final int TIME_FORMAT_24H = 11;
	public static final int TIME_FORMAT_12H = 12;

	// recording and playback
	public final static int PLAYBACK_SAMPLE_RATE = 44100;
	public final static int RECORD_SAMPLE_RATE_44100 = 44100;
	public final static int RECORD_SAMPLE_RATE_8000 = 8000;
	public final static int RECORD_SAMPLE_RATE_16000 = 16000;
	public final static int RECORD_SAMPLE_RATE_32000 = 32000;
	public final static int RECORD_SAMPLE_RATE_48000 = 48000;

	public final static int RECORD_ENCODING_BITRATE_24000 = 24000;
	public final static int RECORD_ENCODING_BITRATE_48000 = 48000;
	public final static int RECORD_ENCODING_BITRATE_96000 = 96000;
	public final static int RECORD_ENCODING_BITRATE_128000 = 128000;
	public final static int RECORD_ENCODING_BITRATE_192000 = 192000;

	public static final int SORT_DATE = 1;
	public static final int SORT_NAME = 2;
	public static final int SORT_DURATION = 3;

//	public final static int RECORD_AUDIO_CHANNELS_COUNT = 2;
	public final static int RECORD_AUDIO_MONO = 1;
	public final static int RECORD_AUDIO_STEREO = 2;
	public final static int RECORD_MAX_DURATION = 14400000; // 240 min 4 hours

	/** Time interval for Recording progress visualisation. */
	public final static int VISUALIZATION_INTERVAL = 1000/SHORT_RECORD_DP_PER_SECOND; //1000 mills/25 dp per sec

	public final static int RECORD_BYTES_PER_SECOND = RECORD_ENCODING_BITRATE_48000 /8; //bits per sec converted to bytes per sec.

}
