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

package com.dimowner.audiorecorder.audio.recorder;

import android.media.MediaRecorder;

import com.dimowner.audiorecorder.exception.InvalidOutputFile;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

import static com.dimowner.audiorecorder.AppConstants.RECORD_AUDIO_CHANNELS_COUNT;
import static com.dimowner.audiorecorder.AppConstants.RECORD_ENCODING_BITRATE;
import static com.dimowner.audiorecorder.AppConstants.RECORD_MAX_DURATION;
import static com.dimowner.audiorecorder.AppConstants.RECORD_SAMPLE_RATE;
import static com.dimowner.audiorecorder.AppConstants.VISUALIZATION_INTERVAL;

public class AudioRecorder implements AudioRecorderContract.UserActions {

    private MediaRecorder recorder = null;
    private File recordFile = null;

    private boolean isPrepared = false;
    private boolean isRecording = false;
    private Timer timerProgress;
    private long progress = 0;

    private AudioRecorderContract.RecorderActions actionsListener;

    public AudioRecorder(AudioRecorderContract.RecorderActions actionsListener) {
        this.actionsListener = actionsListener;
    }

    @Override
    public void prepare(String outputFile) {
        Timber.v("prepare file: %s", outputFile);
        recordFile = new File(outputFile);
        if (recordFile.exists() && recordFile.isFile()) {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(RECORD_AUDIO_CHANNELS_COUNT);
            recorder.setAudioSamplingRate(RECORD_SAMPLE_RATE);
            recorder.setAudioEncodingBitRate(RECORD_ENCODING_BITRATE);
            recorder.setMaxDuration(RECORD_MAX_DURATION);
            recorder.setOutputFile(recordFile.getAbsolutePath());
            try {
                recorder.prepare();
                isPrepared = true;
                if (actionsListener != null) {
                    actionsListener.onPrepareRecord();
                }
            } catch (IOException e) {
                Timber.e(e, "prepare() failed");
                if (actionsListener != null) {
                    actionsListener.onError(e);
                }
            }
        } else {
            if (actionsListener != null) {
                actionsListener.onError(new InvalidOutputFile());
            }
        }
    }

    @Override
    public void startRecording() {
        Timber.v("startRecording");
        if (isPrepared) {
            recorder.start();
            isRecording = true;
            startRecordingTimer();
            if (actionsListener != null) {
                actionsListener.onStartRecord();
            }
        } else {
            Timber.e("Recorder is not prepared!!!");
        }
    }

    @Override
    public void stopRecording() {
        Timber.v("stopRecording");
        if (isRecording) {
            stopRecordingTimer();
            recorder.stop();
            recorder.release();
            if (actionsListener != null) {
                actionsListener.onStopRecord(recordFile);
            }
            recordFile = null;
            isPrepared = false;
            isRecording = false;
            recorder = null;
        } else {
            Timber.e("Recording has already stopped or hasn't started");
        }
    }

    private void startRecordingTimer() {
        timerProgress = new Timer();
        timerProgress.schedule(new TimerTask() {
            @Override
            public void run() {
                if (actionsListener != null && recorder != null) {
                    actionsListener.onRecordProgress(progress, recorder.getMaxAmplitude());
                    progress += VISUALIZATION_INTERVAL;
                }
            }
        }, 0, VISUALIZATION_INTERVAL);
    }

    private void stopRecordingTimer() {
        timerProgress.cancel();
        timerProgress.purge();
        progress = 0;
    }

    public boolean isRecording() {
        return isRecording;
    }
}
