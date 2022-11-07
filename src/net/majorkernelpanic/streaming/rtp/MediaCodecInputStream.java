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

package net.majorkernelpanic.streaming.rtp;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import com.serenegiant.media.MediaCodecUtils;
import com.serenegiant.system.BuildCheck;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !
 */
@SuppressLint("NewApi")
public abstract class MediaCodecInputStream extends InputStream {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = MediaCodecInputStream.class.getSimpleName();

	public static MediaCodecInputStream newInstance(@NonNull final MediaCodec mediaCodec) {
		if (BuildCheck.isAPI21()) {
			return new MediaCodecInputStreamApi21(mediaCodec);
		} else {
			return new MediaCodecInputStreamOld(mediaCodec);
		}
	}

	@NonNull
	protected final MediaCodec mMediaCodec;
	@NonNull
	protected final BufferInfo mBufferInfo = new BufferInfo();
	protected ByteBuffer mBuffer = null;
	protected int mIndex = -1;
	private volatile boolean mClosed = false;

	public MediaFormat mMediaFormat;

	private MediaCodecInputStream(@NonNull final MediaCodec mediaCodec) {
		mMediaCodec = mediaCodec;
	}

	public boolean isClosed() {
		return mClosed;
	}

	@Override
	public void close() {
		mClosed = true;
	}

	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException();
	}

	public int available() {
		if (mBuffer != null) {
			return mBufferInfo.size - mBuffer.position();
		} else {
			return 0;
		}
	}

	@NonNull
	public BufferInfo getLastBufferInfo() {
		return mBufferInfo;
	}

	/**
	 * MediaCodecInputStream implementation for API<21
	 */
	private static class MediaCodecInputStreamOld extends MediaCodecInputStream {
		protected ByteBuffer[] mBuffers;

		@SuppressWarnings("deprecation")
		private MediaCodecInputStreamOld(@NonNull final MediaCodec mediaCodec) {
			super(mediaCodec);
			mBuffers = mMediaCodec.getOutputBuffers();
		}

		@SuppressWarnings("deprecation")
		@Override
		public int read(@NonNull final byte[] buffer, final int offset, final int length) throws IOException {
			int min = 0;

			try {
				ByteBuffer buf = mBuffer;
				if (buf == null) {
					while (!Thread.interrupted() && !isClosed()) {
						mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
						if (mIndex >= 0) {
							//Log.d(TAG,"Index: "+mIndex+" Time: "+mBufferInfo.presentationTimeUs+" size: "+mBufferInfo.size);
							buf = mBuffers[mIndex];
							buf.position(0);
							break;
						} else if (mIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
							mBuffers = mMediaCodec.getOutputBuffers();
						} else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							mMediaFormat = mMediaCodec.getOutputFormat();
							if (DEBUG) MediaCodecUtils.dump(mMediaFormat);
						} else if (mIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
							Log.e(TAG, "Message: " + mIndex);
							//return 0;
						}
					}
				}

				if (isClosed()) throw new IOException("This InputStream was closed");

				if (buf != null) {
					min = Math.min(length, mBufferInfo.size - buf.position());
					buf.get(buffer, offset, min);
					if (buf.position() >= mBufferInfo.size) {
						mMediaCodec.releaseOutputBuffer(mIndex, false);
						buf = null;
					}
				}
				mBuffer = buf;
			} catch (final RuntimeException e) {
				e.printStackTrace();
			}

			return min;
		}
	}

	/**
	 * MediaCodecInputStream implementation for API>=21
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static class MediaCodecInputStreamApi21 extends MediaCodecInputStream {
		private MediaCodecInputStreamApi21(@NonNull final MediaCodec mediaCodec) {
			super(mediaCodec);
		}

		@Override
		public int read(@NonNull final byte[] buffer, final int offset, final int length) throws IOException {
			int min = 0;

			try {
				ByteBuffer buf = mBuffer;
				if (buf == null) {
					while (!Thread.interrupted() && !isClosed()) {
						mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
						if (mIndex >= 0) {
							//Log.d(TAG,"Index: "+mIndex+" Time: "+mBufferInfo.presentationTimeUs+" size: "+mBufferInfo.size);
							buf = mMediaCodec.getOutputBuffer(mIndex);
							buf.position(0);
							break;
						} else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							mMediaFormat = mMediaCodec.getOutputFormat();
							if (DEBUG) Log.i(TAG, mMediaFormat.toString());
						} else if (mIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
							Log.e(TAG, "Message: " + mIndex);
							//return 0;
						}
					}
				}

				if (isClosed()) throw new IOException("This InputStream was closed");

				if (buf != null) {
					min = Math.min(length, mBufferInfo.size - buf.position());
					buf.get(buffer, offset, min);
					if (buf.position() >= mBufferInfo.size) {
						mMediaCodec.releaseOutputBuffer(mIndex, false);
						buf = null;
					}
				}
				mBuffer = buf;
			} catch (final RuntimeException e) {
				e.printStackTrace();
			}

			return min;
		}
	}
}
