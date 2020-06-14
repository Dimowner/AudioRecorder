package com.dimowner.audiorecorder.exception;

/**
 * Created on 14.06.2020.
 * @author Dimowner
 */
public class FailedToRestoreRecord extends AppException {
	@Override
	public int getType() {
		return AppException.FAILED_TO_RESTORE;
	}
}
