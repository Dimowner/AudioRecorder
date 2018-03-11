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

package com.dimowner.audiorecorder.audio.player;

import android.media.AudioManager;
import android.media.MediaPlayer;
import com.dimowner.audiorecorder.util.AndroidUtils;
import java.io.IOException;
import timber.log.Timber;

public class AudioPlayer implements AudioPlayerContract.UserActions {

    private AudioPlayerContract.PlayerActions actionsListener;

    private MediaPlayer mediaPlayer;

    public AudioPlayer(AudioPlayerContract.PlayerActions playerActions) {
        this.actionsListener = playerActions;
    }

    @Override
    public void setData(String data) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(data);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
//            if (actionsListener != null) {
//                actionsListener.onAddRecord(mediaPlayer.getDuration());
//            }
        } catch (IOException e) {
            Timber.e(e);
            actionsListener.onError(e);
        }

    }

    @Override
    public void clearData() {
        stop();
    }

    @Override
    public void playOrPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                stop();
            } else {
                try {
                    mediaPlayer.prepare();
                    if (actionsListener != null) {
                        actionsListener.onPreparePlay();
                    }
                    mediaPlayer.start();
                    if (actionsListener != null) {
                        actionsListener.onStartPlay();
                    }
                } catch (IOException e) {
                    Timber.e(e);
                    actionsListener.onError(e);
                }
            }
        }
    }

    @Override
    public void seek(int pixels) {
        double mills = AndroidUtils.convertPxToMills(pixels);
        mediaPlayer.seekTo((int) mills);
        if (actionsListener != null) {
            actionsListener.onSeek((int) mills);
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                if (actionsListener != null) {
                    actionsListener.onStopPlay();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }

    public void stopListenActions() {
        if (actionsListener != null) {
            actionsListener = null;
        }
    }
}
