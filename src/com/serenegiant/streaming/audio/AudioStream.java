/*
 * Copyright (C) 2014 - 2022 t_saki t_saki@serenegiant.com
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

package com.serenegiant.streaming.audio;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.audio.IAudioStream;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * Don't use this class directly.
 */
public abstract class AudioStream extends MediaStream implements IAudioStream {

	private static final String TAG = AudioStream.class.getSimpleName();

	private AudioSource.Factory mFactory;

	protected int mOutputFormat;
	protected int mAudioEncoder;
	@NonNull
	protected AudioQuality mRequestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
	@NonNull
	protected AudioQuality mQuality = mRequestedQuality.clone();

	public AudioStream() {
		super();
	}

	@Override
	public void setAudioQuality(@NonNull final AudioQuality quality) {
		mRequestedQuality = quality;
	}

	/**
	 * Returns the quality of the stream.
	 */
	@NonNull
	@Override
	public AudioQuality getAudioQuality() {
		return mQuality;
	}

	public void setFactory(@NonNull final AudioSource.Factory factory) {
		mFactory = factory;
	}

	protected void setAudioEncoder(int audioEncoder) {
		mAudioEncoder = audioEncoder;
	}

	protected void setOutputFormat(int outputFormat) {
		mOutputFormat = outputFormat;
	}

	protected AudioSource createAudioSource() {
		if (mFactory != null) {
			return mFactory.createAudioSource(mQuality);
		} else {
			return AudioRecordSource.FACTORY.createAudioSource(mQuality);
		}
	}

	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		throw new UnsupportedOperationException("This class does not support MediaRecorder");
	}

}
