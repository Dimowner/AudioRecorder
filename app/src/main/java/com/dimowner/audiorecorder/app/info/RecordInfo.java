package com.dimowner.audiorecorder.app.info;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created on 28.12.2019.
 * @author Dimowner
 */
public class RecordInfo implements Parcelable {

	private String name;
	private String format;
	private String location;
	private long duration;
	private long created;
	private long size;
	private int sampleRate;
	private int channelCount;
	private int bitrate;

	public RecordInfo(String name, String format, long duration, long size, String location, long created,
							int sampleRate, int channelCount, int bitrate) {
		this.name = name;
		this.format = format;
		this.duration = duration;
		this.size = size;
		this.location = location;
		this.created = created;
		this.sampleRate = sampleRate;
		this.channelCount = channelCount;
		this.bitrate = bitrate;
	}

	public String getName() {
		return name;
	}

	public String getFormat() {
		return format;
	}

	public long getDuration() {
		return duration;
	}

	public long getSize() {
		return size;
	}

	public String getLocation() {
		return location;
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
	private RecordInfo(Parcel in) {
		String[] data = new String[3];
		in.readStringArray(data);
		name = data[0];
		format = data[1];
		location = data[2];
		long[] longs = new long[3];
		in.readLongArray(longs);
		duration = longs[0];
		size = longs[1];
		created = longs[2];
		int[] ints = new int[3];
		in.readIntArray(ints);
		sampleRate = ints[0];
		channelCount = ints[1];
		bitrate = ints[2];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeStringArray(new String[] {name, format, location});
		out.writeLongArray(new long[] {duration, size, created});
		out.writeIntArray(new int[] {sampleRate, channelCount, bitrate});
	}

	public static final Parcelable.Creator<RecordInfo> CREATOR
			= new Parcelable.Creator<RecordInfo>() {
		public RecordInfo createFromParcel(Parcel in) {
			return new RecordInfo(in);
		}

		public RecordInfo[] newArray(int size) {
			return new RecordInfo[size];
		}
	};
	//----- END Parcelable implementation ----------
}
