# Develop Plans
<b>Nearest Release v1.0.0:</b>
 - Fix few main issues and bugs
 - Add support Bluetooth mics
 - Add ability to select recording audio source
 - Add backup feature to allow download all records files into a public directory 'Downloads'

<br>
<p><b>Release v2.0.0:</b> (Currently under development)</p>
<p>Completely rework the app by adopting a new architecture written in Kotlin, designing the UI with Android Compose, integrating a Room database, using ExoPlayer for media playback, and Hilt for dependency injection. Despite these substantial changes, the app’s existing user interface will remain mostly unchanged. Additionally, focusing on achieving robust unit test coverage. These enhancements are expected to significantly enhance app stability, accelerate feature delivery, and mitigate concerns about introducing new bugs.</p>


![Audio Recorder Logo](https://github.com/Dimowner/AudioRecorder/blob/master/app/src/releaseConfig/res/mipmap-xxxhdpi/audio_recorder_logo.png)

# Audio Recorder

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.dimowner.audiorecorder/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.dimowner.audiorecorder)


<p><b>Audio Recorder</b> is your go-to app for seamless audio recording. Whether you’re capturing important moments or creating content, Audio Recorder got you covered. Here’s what makes our app stand out:</p>

<p><b>1. Fast Startup</b>: We’ve optimized the app to launch quickly, so you won’t miss a beat when inspiration strikes.</p>
<b>2. Flexible Formats</b>: Choose from three recording formats:

 - <b>M4A</b>: High-quality audio in a compact file size.
 - <b>WAVE (WAV)</b>: Ideal for professional-grade recordings.
 - <b>3Gp</b>: Perfect for sharing on the go.

<b>3. Customizable Settings:</b>
 - Easy to adjust sample rate and bitrate to suit your needs.
 - Toggle between mono and stereo recording.

<b>4. Visual Waveform</b>: See your recordings come to life with waveform display.

<b>5. User-Friendly Features:</b>
 - Rename recordings for easy organization.
 - Share your audio files effortlessly.
 - Import existing audio files.
 - Create bookmarks for quick access.
 - Personalize the app with colorful themes.

# FAQ
### <b>When option to choose recording deirectory will be added?</b>
<p>There is no planst to add feature where users can choose recording directory. Newer versions of Android added restrictions on ability to interact with device file system. So all recoreds are stored in app's private dir. It is not going to change. Anyway, all record files available for user to dowload from app's privete dir to public dir</p> 

### License

```
Copyright 2019 Dmytro Ponomarenko

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements. See the NOTICE file distributed with this work for
additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
```
