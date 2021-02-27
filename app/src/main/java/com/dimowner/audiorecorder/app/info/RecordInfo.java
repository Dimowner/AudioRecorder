package com.dimowner.audiorecorder.app.info;

import android.os.Parcel;
import android.os.Parcelable;

import com.dimowner.audiorecorder.AppConstants;

/**
 * Created on 28.12.2019.
 * @author Dimowner
 */
public class RecordInfo implements Parcelable {

	private final String name;
	private final String format;
	private final String location;
	private final long duration;
	private final long created;
	private final long size;
	private final int sampleRate;
	private final int channelCount;
	private final int bitrate;
	private boolean isInDatabase;
	private final boolean isInTrash;

	public RecordInfo(String name, String format, long duration, long size, String location, long created,
							int sampleRate, int channelCount, int bitrate, boolean isInTrash) {
		this.name = name;
		this.format = format;
		this.duration = duration;
		this.size = size;
		this.location = location;
		this.created = created;
		this.sampleRate = sampleRate;
		this.channelCount = channelCount;
		this.bitrate = bitrate;
		this.isInDatabase = true;
		this.isInTrash = isInTrash;
	}

	public String getName() {
		return name;
	}

	public String getNameWithExtension() {
		return name + AppConstants.EXTENSION_SEPARATOR + format;
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

	public boolean isInDatabase() {
		return isInDatabase;
	}

	public void setInDatabase(boolean inDatabase) {
		isInDatabase = inDatabase;
	}

	public boolean isInTrash() {
		return isInTrash;
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
		boolean[] booleans = new boolean[2];
		in.readBooleanArray(booleans);
		isInDatabase = booleans[0];
		isInTrash = booleans[1];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeStringArray(new String[] {name, format, location});
		out.writeLongArray(new long[] {duration, size, created});
		out.writeIntArray(new int[] {sampleRate, channelCount, bitrate});
		out.writeBooleanArray(new boolean[] {isInDatabase, isInTrash});
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
