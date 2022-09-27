/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 * Copyright (C) 2014 - 2022 t_saki t_saki@serenegiant.com
 *
 * created based on AACStream in libstreaming
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
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Log;

import com.serenegiant.system.BuildCheck;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.rtp.AACADTSPacketizer;
import net.majorkernelpanic.streaming.rtp.AACLATMPacketizer;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * A class for streaming AAC from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationAddress(InetAddress)}, {@link #setDestinationPorts(int)} and {@link #setAudioQuality(AudioQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class AACStream extends AudioStream {

	private static final String TAG = AACStream.class.getSimpleName();

	/**
	 * MPEG-4 Audio Object Types supported by ADTS.
	 **/
	private static final String[] AUDIO_OBJECT_TYPES = {
		"NULL",                              // 0
		"AAC Main",                          // 1
		"AAC LC (Low Complexity)",          // 2
		"AAC SSR (Scalable Sample Rate)", // 3
		"AAC LTP (Long Term Prediction)"  // 4	
	};

	/**
	 * There are 13 supported frequencies by ADTS.
	 **/
	public static final int[] AUDIO_SAMPLING_RATES = {
		96000, // 0
		88200, // 1
		64000, // 2
		48000, // 3
		44100, // 4
		32000, // 5
		24000, // 6
		22050, // 7
		16000, // 8
		12000, // 9
		11025, // 10
		8000,  // 11
		7350,  // 12
		-1,   // 13
		-1,   // 14
		-1,   // 15
	};

	private String mSessionDescription = null;
	private int mProfile, mSamplingRateIndex, mChannel, mConfig;
	private Thread mThread = null;

	public AACStream() {
		super();

		if (!AACStreamingSupported()) {
			Log.e(TAG, "AAC not supported on this phone");
			throw new RuntimeException("AAC not supported by this phone !");
		} else {
			Log.d(TAG, "AAC supported on this phone");
		}

	}

	private static boolean AACStreamingSupported() {
		if (Build.VERSION.SDK_INT < 14) return false;
		try {
			MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public synchronized void start() throws IllegalStateException, IOException {
		if (!isStreaming()) {
			configure();
			super.start();
		}
	}

	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mQuality = mRequestedQuality.clone();

		// Checks if the user has supplied an exotic sampling rate
		int i = 0;
		for (; i < AUDIO_SAMPLING_RATES.length; i++) {
			if (AUDIO_SAMPLING_RATES[i] == mQuality.samplingRate) {
				mSamplingRateIndex = i;
				break;
			}
		}
		// If he did, we force a reasonable one: 16 kHz
		if (i > 12) mQuality.samplingRate = 16000;

		if (mMode != mRequestedMode || mPacketizer == null) {
			mMode = mRequestedMode;
			if (mMode == MODE_MEDIARECORDER_API) {
				mPacketizer = new AACADTSPacketizer();
			} else {
				mPacketizer = new AACLATMPacketizer();
			}
			mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
			mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
		}

		if (mMode == MODE_MEDIARECORDER_API) {
			throw new UnsupportedOperationException("This class does not support MediaRecorder");
		} else {

			mProfile = 2; // AAC LC
			mChannel = 1;
			mConfig = (mProfile & 0x1F) << 11 | (mSamplingRateIndex & 0x0F) << 7 | (mChannel & 0x0F) << 3;

			mSessionDescription = "m=audio " + getDestinationPorts()[0] + " RTP/AVP 96\r\n" +
				"a=rtpmap:96 mpeg4-generic/" + mQuality.samplingRate + "\r\n" +
				"a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config=" + Integer.toHexString(mConfig) + "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n";

		}

	}

	@Override
	protected void encodeWithMediaRecorder() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void encodeWithMediaCodec() throws IOException {
		if (BuildCheck.isAPI21()) {
			encodeWithMediaCodecAPI21();
		} else {
			encodeWithMediaCodecOld();
		}
	}

	@SuppressWarnings("deprecation")
	@SuppressLint({"InlinedApi", "NewApi", "MissingPermission"})
	private void encodeWithMediaCodecOld() throws IOException {

		final AudioSource audioSource = createAudioSource();
		final int bufferSize = audioSource.getBufferSize();

		((AACLATMPacketizer) mPacketizer).setSamplingRate(mQuality.samplingRate);

		mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
		MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		audioSource.start();
		mMediaCodec.start();

		final MediaCodecInputStream inputStream = MediaCodecInputStream.newInstance(mMediaCodec);
		final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				int len, bufferIndex;
				try {
					while (!Thread.interrupted() && isStreaming()) {
						bufferIndex = mMediaCodec.dequeueInputBuffer(10000/*10ms*/);
						if (bufferIndex >= 0) {
							inputBuffers[bufferIndex].clear();
							len = audioSource.read(inputBuffers[bufferIndex], bufferSize);
							if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
								Log.e(TAG, "An error occurred with the AudioRecord API !");
							} else {
								//Log.v(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
								mMediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime() / 1000, 0);
							}
						}
					}
				} catch (final RuntimeException e) {
					e.printStackTrace();
				} finally {
					audioSource.stop();
					audioSource.release();
				}
			}
		});
		mThread = thread;
		thread.start();

		// The packetizer encapsulates this stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(inputStream);
		mPacketizer.start();

	}

	@SuppressLint({"InlinedApi", "NewApi", "MissingPermission"})
	private void encodeWithMediaCodecAPI21() throws IOException {

		final AudioSource audioSource = createAudioSource();
		final int bufferSize = audioSource.getBufferSize();

		((AACLATMPacketizer) mPacketizer).setSamplingRate(mQuality.samplingRate);

		mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
		final MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		audioSource.start();
		mMediaCodec.start();

		final MediaCodecInputStream inputStream = MediaCodecInputStream.newInstance(mMediaCodec);

		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				int len, bufferIndex;
				try {
					while (!Thread.interrupted() && isStreaming()) {
						bufferIndex = mMediaCodec.dequeueInputBuffer(10000/*10ms*/);
						if (bufferIndex >= 0) {
							final ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(bufferIndex);
							inputBuffer.clear();
							len = audioSource.read(inputBuffer, bufferSize);
							if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
								Log.e(TAG, "An error occurred with the AudioRecord API !");
							} else {
								//Log.v(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
								mMediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime() / 1000, 0);
							}
						}
					}
				} catch (final RuntimeException e) {
					e.printStackTrace();
				} finally {
					audioSource.stop();
					audioSource.release();
				}
			}
		});
		mThread = thread;
		thread.start();

		// The packetizer encapsulates this stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(inputStream);
		mPacketizer.start();

	}

	/**
	 * Stops the stream.
	 */
	public synchronized void stop() {
		if (isStreaming()) {
			if (mMode == MODE_MEDIACODEC_API) {
				Log.d(TAG, "Interrupting threads...");
				final Thread thread = mThread;
				mThread = null;
				if (thread != null) {
					thread.interrupt();
				}
			}
			super.stop();
		}
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * Will fail if called when streaming.
	 */
	public String getSessionDescription() throws IllegalStateException {
		if (mSessionDescription == null)
			throw new IllegalStateException("You need to call configure() first !");
		return mSessionDescription;
	}

}
