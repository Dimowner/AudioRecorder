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

package com.dimowner.audiorecorder.data.database;

import java.util.Arrays;
import timber.log.Timber;

public class Record {

	public static final int NO_ID = -1;
	private static final String DELIMITER = ",";

	private int id;
	private String name;
	private long duration;
	private long created;
	private String path;
	private int[] amps;
	private byte[] data;
	private String dataStr;
	//TODO: Add duration field.


	public Record(int id, String name, long duration, long created, String path, int[] amps) {
		this.id = id;
		this.name = name;
		this.duration = duration;
		this.created = created;
		this.path = path;
		this.amps = amps;
//		TODO: fix byte array
		this.data = new byte[1];
		this.dataStr = arrayToString(amps);
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getCreated() {
		return created;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int[] getAmps() {
		return amps;
	}

	public long getDuration() {
		return duration;
	}

	public byte[] getData() {
		return data;
	}

	public String getDataStr() {
		return dataStr;
	}

	public static int[] stringToArray(String groups) {
		if (groups != null && !groups.isEmpty()) {
			String[] grStr = groups.split(DELIMITER);
			int[] grInt = new int[grStr.length];
			for (int i = 0; i < grStr.length; i++) {
				try {
					grInt[i] = Integer.parseInt(grStr[i]);
				} catch (NumberFormatException e) {
					Timber.e(e);
				}
			}
			return grInt;
		}
		return new int[0];
	}

	public static String arrayToString(int[] tokens) {
		StringBuilder sb = new StringBuilder();
		boolean firstTime = true;
		for (Object token: tokens) {
			if (firstTime) {
				firstTime = false;
			} else {
				sb.append(DELIMITER);
			}
			sb.append(token);
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return "Record{" +
				"id=" + id +
				", name='" + name + '\'' +
				", duration='" + duration + '\'' +
				", created=" + created +
				", path='" + path + '\'' +
				", data=" + Arrays.toString(data) +
				", dataStr='" + dataStr + '\'' +
				'}';
	}
}
