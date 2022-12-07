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

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

import net.majorkernelpanic.streaming.audio.AudioQuality;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;

/**
 * implementation of IAudioSource, wrapped AudioRecord
 */
public class AudioRecordSource implements AudioSource {
	public static AudioSource.Factory FACTORY
		= new AudioRecordSourceFactory(MediaRecorder.AudioSource.CAMCORDER);

	public static class AudioRecordSourceFactory implements AudioSource.Factory {
		private final int mAudioSource;

		public AudioRecordSourceFactory(final int audioSource) {
			mAudioSource = audioSource;
		}

		@NonNull
		@Override
		public AudioSource createAudioSource(@NonNull final AudioQuality audioQuality) {
			try {
				return new AudioRecordSource(mAudioSource, audioQuality.samplingRate,
					AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
			} catch (final IOException e) {
				throw new UnsupportedOperationException(e);
			}
		}
	}

	private final int mBufferSize;
	@NonNull
	private final AudioRecord mAudioRecord;

	@SuppressLint("MissingPermission")
	public AudioRecordSource(
		final int audioSource, final int sampleRateInHz,
		final int channelConfig, final int audioFormat) throws IOException {

		mBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 2;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			mAudioRecord = new AudioRecord.Builder()
				.setAudioSource(audioSource)
				.setAudioFormat(new AudioFormat.Builder()
					.setEncoding(audioFormat)
					.setSampleRate(sampleRateInHz)
					.setChannelMask(channelConfig)
					.build())
				.setBufferSizeInBytes(mBufferSize)
				.build();
		} else {
			mAudioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, mBufferSize);
		}
		if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
			throw new IOException("Failed to initialize AudioRecord");
		}
	}

	@Override
	public void release() {
		stop();
		mAudioRecord.release();
	}

	@Override
	public int getBufferSize() {
		return mBufferSize;
	}

	@Override
	public void start() {
		if ((mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED)
			&& (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)) {
			mAudioRecord.startRecording();
		}
	}

	@Override
	public void stop() {
		if ((mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED)
			&& (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)) {
			mAudioRecord.stop();
		}
	}

	@Override
	public int read(@NonNull final ByteBuffer buffer, final int size) {
		return mAudioRecord.read(buffer, size);
	}

}
