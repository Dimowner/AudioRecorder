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

package com.dimowner.audiorecorder.app.lostrecords;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created on 14.12.2019.
 * @author Dimowner
 */
public class RecordItem implements Parcelable {
	private int id;
	private String name;
	private String format;
	private long duration;
	private long size;
	private String path;
	private long created;
	private int sampleRate;
	private int channelCount;
	private int bitrate;

	public RecordItem(int id, String name, long size, String format, long duration, String path, long created,
							int sampleRate, int channelCount, int bitrate) {
		this.id = id;
		this.name = name;
		this.size = size;
		this.format = format;
		this.duration = duration;
		this.path = path;
		this.created = created;
		this.sampleRate = sampleRate;
		this.channelCount = channelCount;
		this.bitrate = bitrate;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}

	public String getFormat() {
		return format;
	}

	public long getDuration() {
		return duration;
	}

	public String getPath() {
		return path;
	}

	public long getCreated() {
		return created;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public int getChannelCount() {
		return channelCount;
	}

	public int getBitrate() {
		return bitrate;
	}

	//----- START Parcelable implementation ----------
	private RecordItem(Parcel in) {
		long[] longs = new long[3];
		in.readLongArray(longs);
		duration = longs[0];
		size = longs[1];
		created = longs[2];
		String[] data = new String[3];
		in.readStringArray(data);
		name = data[0];
		format = data[1];
		path = data[2];
		int[] ints = new int[4];
		in.readIntArray(ints);
		id = ints[0];
		sampleRate = ints[1];
		channelCount = ints[2];
		bitrate = ints[3];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeLongArray(new long[] {duration, size, created});
		out.writeStringArray(new String[] {name, format, path});
		out.writeIntArray(new int[] {id, sampleRate, channelCount, bitrate});
	}

	public static final Parcelable.Creator<RecordItem> CREATOR
			= new Parcelable.Creator<RecordItem>() {
		public RecordItem createFromParcel(Parcel in) {
			return new RecordItem(in);
		}

		public RecordItem[] newArray(int size) {
			return new RecordItem[size];
		}
	};
	//----- END Parcelable implementation ----------
}
