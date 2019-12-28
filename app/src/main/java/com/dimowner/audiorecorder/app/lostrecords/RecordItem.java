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
	private long duration;
	private String path;
	private long created;

	public RecordItem(int id, String name, long duration, String path, long created) {
		this.id = id;
		this.name = name;
		this.duration = duration;
		this.path = path;
		this.created = created;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
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

	//----- START Parcelable implementation ----------
	private RecordItem(Parcel in) {
		id = in.readInt();
		long[] longs = new long[2];
		in.readLongArray(longs);
		duration = longs[0];
		created = longs[1];
		String[] data = new String[2];
		in.readStringArray(data);
		name = data[0];
		path = data[1];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeLongArray(new long[] {duration, created});
		out.writeStringArray(new String[] {name, path});
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
