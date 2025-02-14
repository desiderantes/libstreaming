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

package net.majorkernelpanic.streaming.video;

import java.util.Iterator;
import java.util.List;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A class that represents the quality of a video stream. 
 * It contains the resolution, the framerate (in fps) and the bitrate (in bps) of the stream.
 */
public class VideoQuality implements Cloneable {

	private static final String TAG = VideoQuality.class.getSimpleName();
	
	/** Default video stream quality. */
	public final static VideoQuality DEFAULT_VIDEO_QUALITY = new VideoQuality(176,144,20,500000);

	/**	Represents a quality for a video stream. */ 
	public VideoQuality() {}

	/**
	 * Represents a quality for a video stream.
	 * @param resX The horizontal resolution
	 * @param resY The vertical resolution
	 */
	public VideoQuality(int resX, int resY) {
		this.resX = resX;
		this.resY = resY;
	}	

	/**
	 * Represents a quality for a video stream.
	 * @param resX The horizontal resolution
	 * @param resY The vertical resolution
	 * @param framerate The framerate in frame per seconds
	 * @param bitrate The bitrate in bit per seconds 
	 */
	public VideoQuality(int resX, int resY, int framerate, int bitrate) {
		this.framerate = framerate;
		this.bitrate = bitrate;
		this.resX = resX;
		this.resY = resY;
	}

	public int framerate = 0;
	public int bitrate = 0;
	public int resX = 0;
	public int resY = 0;

	public boolean equals(VideoQuality quality) {
		if (quality==null) return false;
		return (quality.resX == this.resX 			&&
				quality.resY == this.resY 			&&
				quality.framerate == this.framerate	&&
				quality.bitrate == this.bitrate);
	}

	@NonNull
	public VideoQuality clone() {
		final VideoQuality result;
		try {
			result = (VideoQuality) super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		result.resX = resX;
		result.resY = resY;
		result.framerate = framerate;
		result.bitrate = bitrate;
		return result;
	}

	public static VideoQuality parseQuality(@Nullable final String str, @Nullable final VideoQuality defaultQuality) {
		final VideoQuality quality = defaultQuality != null ? defaultQuality : DEFAULT_VIDEO_QUALITY.clone();
		if (str != null) {
			final String[] config = str.split("-");
			try {
				quality.bitrate = Integer.parseInt(config[0])*1000; // conversion to bit/s
				quality.framerate = Integer.parseInt(config[1]);
				quality.resX = Integer.parseInt(config[2]);
				quality.resY = Integer.parseInt(config[3]);
			} catch (final IndexOutOfBoundsException e) {
				Log.w(TAG, e);
			}
		}
		return quality;
	}

	@NonNull
	@Override
	public String toString() {
		return resX+"x"+resY+" px, "+framerate+" fps, "+bitrate/1000+" kbps";
	}
	
	/** 
	 * Checks if the requested resolution is supported by the camera.
	 * If not, it modifies it by supported parameters. 
	 **/
	public static VideoQuality determineClosestSupportedResolution(Camera.Parameters parameters, VideoQuality quality) {
		VideoQuality v = quality.clone();
		int minDist = Integer.MAX_VALUE;
		final StringBuilder supportedSizesStr = new StringBuilder("Supported resolutions: ");
		List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
		for (Iterator<Size> it = supportedSizes.iterator(); it.hasNext();) {
			Size size = it.next();
			supportedSizesStr.append(size.width).append("x").append(size.height).append(it.hasNext() ? ", " : "");
			int dist = Math.abs(quality.resX - size.width);
			if (dist<minDist) {
				minDist = dist;
				v.resX = size.width;
				v.resY = size.height;
			}
		}
		Log.v(TAG, supportedSizesStr.toString());
		if (quality.resX != v.resX || quality.resY != v.resY) {
			Log.v(TAG,"Resolution modified: "+quality.resX+"x"+quality.resY+"->"+v.resX+"x"+v.resY);
		}
		
		return v;
	}

	public static int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
		int[] maxFps = new int[]{0,0};
		final StringBuilder supportedFpsRangesStr = new StringBuilder("Supported frame rates: ");
		List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
		for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext();) {
			int[] interval = it.next();
			// Intervals are returned as integers, for example "29970" means "29.970" FPS.
			supportedFpsRangesStr.append(interval[0] / 1000).append("-").append(interval[1] / 1000).append("fps").append(it.hasNext() ? ", " : "");
			if (interval[1]>maxFps[1] || (interval[0]>maxFps[0] && interval[1]==maxFps[1])) {
				maxFps = interval; 
			}
		}
		Log.v(TAG, supportedFpsRangesStr.toString());
		return maxFps;
	}
	
}
