package com.dimowner.audiorecorder.data.database;

import java.util.List;

/**
 * Created on 14.12.2019.
 * @author Dimowner
 */
public interface OnRecordsLostListener {
	void onLostRecords(List<Record> list);
}
