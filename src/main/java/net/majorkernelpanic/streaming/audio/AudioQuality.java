/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
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

package net.majorkernelpanic.streaming.audio;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A class that represents the quality of an audio stream.
 */
public class AudioQuality implements Cloneable {

	private static final String TAG = AudioQuality.class.getSimpleName();

	/** Default audio stream quality. */
	public final static AudioQuality DEFAULT_AUDIO_QUALITY = new AudioQuality(8000,32000);

	/**	Represents a quality for a video stream. */ 
	public AudioQuality() {}

	/**
	 * Represents a quality for an audio stream.
	 * @param samplingRate The sampling rate
	 * @param bitRate The bitrate in bit per seconds
	 */
	public AudioQuality(int samplingRate, int bitRate) {
		this.samplingRate = samplingRate;
		this.bitRate = bitRate;
	}	

	public int samplingRate = 0;
	public int bitRate = 0;

	public boolean equals(AudioQuality quality) {
		if (quality==null) return false;
		return (quality.samplingRate == this.samplingRate     &&
				quality.bitRate == this.bitRate);
	}

	@NonNull
	public AudioQuality clone() {
		final AudioQuality result;
		try {
			result = (AudioQuality) super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		result.samplingRate = samplingRate;
		result.bitRate = bitRate;
		return result;
	}

	@NonNull
	@Override
	public String toString() {
		return "AudioQuality{" +
			"samplingRate=" + samplingRate +
			", bitRate=" + bitRate +
			'}';
	}

	public static AudioQuality parseQuality(@Nullable final String str, @Nullable final AudioQuality defaultQuality) {
		final AudioQuality quality = defaultQuality != null ? defaultQuality : DEFAULT_AUDIO_QUALITY.clone();
		if (!TextUtils.isEmpty(str)) {
			final String[] config = str.split("-");
			try {
				if (config.length > 0) {
					quality.bitRate = Integer.parseInt(config[0])*1000; // conversion to bit/s
				}
				if (config.length > 1) {
					quality.samplingRate = Integer.parseInt(config[1]);
				}
			} catch (final IndexOutOfBoundsException e) {
				Log.w(TAG, e);
			}
		}
		return quality;
	}

}
