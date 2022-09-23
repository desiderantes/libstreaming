/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
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

package com.serenegiant.streaming.video;

import android.annotation.SuppressLint;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.streaming.hw.EncoderDebugger;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.exceptions.ConfNotSupportedException;
import net.majorkernelpanic.streaming.exceptions.StorageUnavailableException;
import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A class for streaming H.264 from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationPorts(int)} and {@link #setVideoQuality(VideoQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class H264Stream extends VideoStream {
	private static final boolean DEBUG = false;	// set false on prduction
	private static final String TAG = H264Stream.class.getSimpleName();

	private final Semaphore mLock = new Semaphore(0);
	private MP4Config mConfig;

	/**
	 * Constructs the H.264 stream.
	 * Uses CAMERA_FACING_BACK by default.
	 */
	public H264Stream() {
		super();
		mMimeType = "video/avc";
		mVideoEncoder = MediaRecorder.VideoEncoder.H264;
		mPacketizer = new H264Packetizer();
		mMode = MODE_MEDIACODEC_API_2;
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 */
	public synchronized String getSessionDescription() throws IllegalStateException {
		if (mConfig == null) throw new IllegalStateException("You need to call configure() first !");
		return "m=video "+ getDestinationPorts()[0] +" RTP/AVP 96\r\n" +
		"a=rtpmap:96 H264/90000\r\n" +
		"a=fmtp:96 packetization-mode=1;profile-level-id="+mConfig.getProfileLevel()+";sprop-parameter-sets="+mConfig.getB64SPS()+","+mConfig.getB64PPS()+";\r\n";
	}

	/**
	 * Starts the stream.
	 * This will also open the camera and display the preview if {@link #startPreview()} has not already been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		if (!isStreaming()) {
			configure();
			final byte[] pps = Base64.decode(mConfig.getB64PPS(), Base64.NO_WRAP);
			final byte[] sps = Base64.decode(mConfig.getB64SPS(), Base64.NO_WRAP);
			((H264Packetizer)mPacketizer).setStreamParameters(pps, sps);
			super.start();
		}
	}

	/**
	 * Configures the stream. You need to call this before calling {@link #getSessionDescription()} to apply
	 * your configuration of the stream.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mQuality = mRequestedQuality.clone();
		mConfig = testH264();
	}

	/**
	 * Tests if streaming with the given configuration (bit rate, frame rate, resolution) is possible
	 * and determines the pps and sps. Should not be called by the UI thread.
	 **/
	private MP4Config testH264() throws IllegalStateException, IOException {
		if (mMode != MODE_MEDIARECORDER_API) return testMediaCodecAPI();
		else return testMediaRecorderAPI();
	}

	@SuppressLint("NewApi")
	private MP4Config testMediaCodecAPI() throws RuntimeException, IOException {
		try {
			final EncoderDebugger debugger = EncoderDebugger.debug(getSettings(), mQuality.resX, mQuality.resY);
			return new MP4Config(debugger.getB64SPS(), debugger.getB64PPS());
		} catch (Exception e) {
			// Fallback on the old streaming method using the MediaRecorder API
			Log.e(TAG,"Resolution not supported with the MediaCodec API, we fallback on the old streamign method.");
			mMode = MODE_MEDIARECORDER_API;
			return testH264();
		}
	}

	// Should not be called by the UI thread
	private MP4Config testMediaRecorderAPI() throws RuntimeException, IOException {
		String key = PREF_PREFIX+"h264-mr-"+mRequestedQuality.framerate+","+mRequestedQuality.resX+","+mRequestedQuality.resY;

		final Bundle settings = getSettings();
		if (settings.containsKey(key) ) {
			String[] s = settings.getString(key, "").split(",");
			return new MP4Config(s[0],s[1],s[2]);
		}

		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			throw new StorageUnavailableException("No external storage or external storage not ready !");
		}

		final String TESTFILE = Environment.getExternalStorageDirectory().getPath()+"/spydroid-test.mp4";

		Log.i(TAG,"Testing H264 support... Test file saved at: "+TESTFILE);

		try {
			File file = new File(TESTFILE);
			file.createNewFile();
		} catch (IOException e) {
			throw new StorageUnavailableException(e.getMessage());
		}

		boolean previewStarted = mPreviewStarted;

		// Stops the preview if needed
		if (mPreviewStarted) {
			mPreviewStarted = false;
		}

		try {
			Thread.sleep(100);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			mMediaRecorder = new MediaRecorder();
			mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mMediaRecorder.setVideoEncoder(mVideoEncoder);
			mMediaRecorder.setVideoSize(mRequestedQuality.resX,mRequestedQuality.resY);
			mMediaRecorder.setVideoFrameRate(mRequestedQuality.framerate);
			mMediaRecorder.setVideoEncodingBitRate((int)(mRequestedQuality.bitrate*0.8));
			mMediaRecorder.setOutputFile(TESTFILE);
			mMediaRecorder.setMaxDuration(3000);

			// We wait a little and stop recording
			mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
				public void onInfo(MediaRecorder mr, int what, int extra) {
					Log.d(TAG,"MediaRecorder callback called !");
					if (what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
						Log.d(TAG,"MediaRecorder: MAX_DURATION_REACHED");
					} else if (what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
						Log.d(TAG,"MediaRecorder: MAX_FILESIZE_REACHED");
					} else if (what==MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN) {
						Log.d(TAG,"MediaRecorder: INFO_UNKNOWN");
					} else {
						Log.d(TAG,"WTF ?");
					}
					mLock.release();
				}
			});

			// Start recording
			mMediaRecorder.prepare();
			final Surface surface = mMediaRecorder.getSurface();
			mSurfaceId = mSource.addSurface(surface);
			mMediaRecorder.start();

			if (mLock.tryAcquire(6,TimeUnit.SECONDS)) {
				Log.d(TAG,"MediaRecorder callback was called :)");
				Thread.sleep(400);
			} else {
				Log.d(TAG,"MediaRecorder callback was not called after 6 seconds... :(");
			}
		} catch (IOException e) {
			throw new ConfNotSupportedException(e.getMessage());
		} catch (RuntimeException e) {
			throw new ConfNotSupportedException(e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				mMediaRecorder.stop();
			} catch (Exception e) {
				Log.w(TAG, e);
			}
			mMediaRecorder.release();
			mMediaRecorder = null;
			if (previewStarted) {
				// If the preview was started before the test, we try to restart it.
				try {
					startPreview();
				} catch (Exception e) {
					Log.w(TAG, e);
				}
			}
		}

		// Retrieve SPS & PPS & ProfileId with MP4Config
		MP4Config config = new MP4Config(TESTFILE);

		// Delete dummy video
		File file = new File(TESTFILE);
		if (!file.delete()) Log.e(TAG,"Temp file could not be erased");

		Log.i(TAG,"H264 Test succeded...");

		// Save test result
		settings.putString(key, config.getProfileLevel()+","+config.getB64SPS()+","+config.getB64PPS());

		return config;

	}

}
