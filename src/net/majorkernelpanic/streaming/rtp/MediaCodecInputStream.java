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
import com.serenegiant.media.MemMediaQueue;
import com.serenegiant.media.RecycleMediaData;
import com.serenegiant.system.BuildCheck;
import com.serenegiant.utils.ThreadUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !
 */
@SuppressLint("NewApi")
public abstract class MediaCodecInputStream extends InputStream {
	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = MediaCodecInputStream.class.getSimpleName();

	private static final long TIMEOUT_MS = 10;
	private static final long TIMEOUT_USEC = TIMEOUT_MS * 1000L;	// 10ミリ秒

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
	protected final BufferInfo mLastBufferInfo = new BufferInfo();
	@NonNull
	protected final MemMediaQueue mQueue = new MemMediaQueue(4, 200);
	@Nullable
	private RecycleMediaData mData = null;
	private ByteBuffer mBuffer;
	private volatile boolean mClosed = false;
	private volatile long mLastPresentationTimeUs;

	@Nullable
	public MediaFormat mMediaFormat;
	@Nullable
	private Thread mReapThread;

	private MediaCodecInputStream(@NonNull final MediaCodec mediaCodec) {
		mMediaCodec = mediaCodec;
	}

	public boolean isClosed() {
		return mClosed;
	}

	@Override
	public void close() {
		mClosed = true;
		final Thread reapThread = mReapThread;
		mReapThread = null;
		if ((reapThread != null) && !reapThread.isInterrupted()) {
			// wait for a little to reduce InterruptException in MediaCodec
			ThreadUtils.NoThrowSleep(TIMEOUT_MS);
//			reapThread.interrupt();
		}
	}

	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException();
	}

	public int available() {
		return mBuffer != null ? mBuffer.remaining() : 0;
	}

	@Deprecated
	@NonNull
	public BufferInfo getLastBufferInfo() {
		return mLastBufferInfo;
	}

	public long presentationTimeUs() {
		return mLastPresentationTimeUs;
	}

	protected abstract void reap();

	private void startReaper() {
//		if (DEBUG) Log.v(TAG, "startReaper:");
		if (!Thread.interrupted() && !isClosed() && (mReapThread == null)) {
			mReapThread = new Thread(new Runnable() {
				@Override
				public void run() {
					if (DEBUG) Log.i(TAG, "start reaper thread");
					while (!Thread.interrupted() && !isClosed()) {
						try {
							reap();
						} catch (final IllegalStateException e) {
							mClosed = true;
							break;
						} catch (final Exception e) {
							if (DEBUG) Log.w(TAG, "mReapThread#run:", e);
							mClosed = true;
							break;
						}
					}
					if (DEBUG) Log.i(TAG, "reaper thread finished");
				}
			});
			mReapThread.start();
		}
	}

	/**
	 * @param buffer
	 * @param offset
	 * @param length
	 * @return
	 * @throws IOException
	 */
	@Override
	public int read(@NonNull final byte[] buffer, final int offset, final int length) throws IOException {
//		if (DEBUG) Log.v(TAG, "read:");
		int min = 0;
		try {
			RecycleMediaData data = mData;
			if (data == null) {
				startReaper();
				while (!Thread.interrupted() && !isClosed()) {
					data = mQueue.poll(50, TimeUnit.MILLISECONDS);
					if (data != null) {
						data.get(mLastBufferInfo);
						mLastPresentationTimeUs = data.presentationTimeUs();
						mBuffer = data.getRaw().asReadOnlyBuffer();
						break;
					}
				}
			}
			if (isClosed()) throw new IOException("This InputStream was closed");
			if (data != null) {
				final ByteBuffer buf = mBuffer;
				min = Math.min(length, buf.remaining());
				buf.get(buffer, offset, min);
				if (buf.remaining() == 0) {
					mQueue.recycle(data);
					data = null;
				}
			}
			mData = data;
		} catch (final InterruptedException e) {
			mClosed = true;
		} catch (final RuntimeException e) {
			if (DEBUG) Log.w(TAG, "read:", e);
		}

//		if (DEBUG) Log.v(TAG, "read:finished,min=" + min);
		return min;
	}

	/**
	 * MediaCodecInputStream implementation for API<21
	 */
	private static class MediaCodecInputStreamOld extends MediaCodecInputStream {
		@NonNull
		private final BufferInfo mInfo = new BufferInfo();
		private ByteBuffer[] mBuffers;

		@SuppressWarnings("deprecation")
		private MediaCodecInputStreamOld(@NonNull final MediaCodec mediaCodec) {
			super(mediaCodec);
			mBuffers = mMediaCodec.getOutputBuffers();
		}

		@Override
		protected void reap() {
			if (!isClosed()) {
				final RecycleMediaData data = mQueue.obtain();
				if (data != null) {
					boolean queued = false;
					for (int i = 0; i < 3; i++) {
						if (Thread.interrupted() || isClosed()) break;
						final int index = mMediaCodec.dequeueOutputBuffer(mInfo, TIMEOUT_USEC);
						if (Thread.interrupted() || isClosed()) break;
						if (index >= 0) {
							final ByteBuffer buf = mBuffers[index];
							data.set(buf, mInfo);
							mMediaCodec.releaseOutputBuffer(index, false);
//							if (DEBUG) Log.d(TAG, "reap: index=" + index
//								+ ",pts=" + mInfo.presentationTimeUs
//								+ ",offset=" + mInfo.offset
//								+ ",size=" + mInfo.size + "/" + data.size());
							mQueue.queueFrame(data);
							queued = true;
							break;
						} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
							mBuffers = mMediaCodec.getOutputBuffers();
						} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							mMediaFormat = mMediaCodec.getOutputFormat();
							if (DEBUG) MediaCodecUtils.dump(mMediaFormat);
						} else if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
							Log.e(TAG, "Message: " + index);
							//return 0;
						}
					}
					if (!queued) {
						data.recycle();
					}
				} else {
					if (DEBUG) Log.v(TAG, "reap: pool is empty");
				}
			}
		}
	}

	/**
	 * MediaCodecInputStream implementation for API>=21
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static class MediaCodecInputStreamApi21 extends MediaCodecInputStream {
		private final BufferInfo mInfo = new BufferInfo();
		private MediaCodecInputStreamApi21(@NonNull final MediaCodec mediaCodec) {
			super(mediaCodec);
		}

		@Override
		protected void reap() {
			if (!isClosed()) {
				final RecycleMediaData data = mQueue.obtain();
				if (data != null) {
					boolean queued = false;
					for (int i = 0; i < 3; i++) {
						if (Thread.interrupted() || isClosed()) break;
						final int index = mMediaCodec.dequeueOutputBuffer(mInfo, TIMEOUT_USEC);
						if (index >= 0) {
							final ByteBuffer buf = mMediaCodec.getOutputBuffer(index);
							data.set(buf, mInfo);
							mMediaCodec.releaseOutputBuffer(index, false);
//							if (DEBUG) Log.d(TAG, "reap: index=" + index
//								+ ",pts=" + mInfo.presentationTimeUs
//								+ ",offset=" + mInfo.offset
//								+ ",size=" + mInfo.size + "/" + data.size());
							mQueue.queueFrame(data);
							queued = true;
							break;
						} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							mMediaFormat = mMediaCodec.getOutputFormat();
							if (DEBUG) MediaCodecUtils.dump(mMediaFormat);
						} else if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
							Log.e(TAG, "Message: " + index);
							//return 0;
						}
					}
					if (!queued) {
						data.recycle();
					}
				} else {
					if (DEBUG) Log.v(TAG, "reap: pool is empty");
				}
			}
		}
	}
}
