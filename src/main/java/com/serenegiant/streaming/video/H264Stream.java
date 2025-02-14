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

import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Base64;

import com.serenegiant.media.MediaCodecUtils;
import com.serenegiant.media.VideoConfig;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

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
	public H264Stream(final long startTimeNs) {
		super(startTimeNs, MediaRecorder.VideoEncoder.H264);
		mPacketizer = new H264Packetizer(startTimeNs);
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
		final VideoConfig config = new VideoConfig()
			.setCaptureFps(mQuality.framerate)
			.setIFrameIntervals(mQuality.framerate / 30.0f)	// 1秒毎
			.setBPP(mQuality.resX, mQuality.resY, mQuality.bitrate);
		final MediaFormat format = MediaCodecUtils.testVideoMediaFormat(
			mMimeType, mQuality.resX, mQuality.resY, config);
		if (DEBUG) MediaCodecUtils.dump(TAG, format);
		final ByteBuffer spsb = format.getByteBuffer("csd-0");
		final ByteBuffer ppsb = format.getByteBuffer("csd-1");
		final byte[] sps = new byte[spsb.capacity()-4];
		spsb.position(4);
		spsb.get(sps);
		final byte[] pps = new byte[ppsb.capacity()-4];
		ppsb.position(4);
		ppsb.get(pps);
		return new MP4Config(sps, pps);
	}

}
