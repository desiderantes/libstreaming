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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.media.MediaCodecUtils;
import com.serenegiant.streaming.MediaStream;

import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.exceptions.ConfNotSupportedException;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import net.majorkernelpanic.streaming.video.IVideoStream;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoSource;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream implements IVideoStream {
	private final static String TAG = VideoStream.class.getSimpleName();

	@NonNull
	protected VideoQuality mRequestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	@NonNull
	protected VideoQuality mQuality = mRequestedQuality.clone();
	@Nullable
	private VideoSource.Factory mFactory = null;
	private VideoSource mSource = null;
	private int mSurfaceId;
	private final int mVideoEncoder;
	protected int mRequestedOrientation = 0, mOrientation = 0;

	protected final String mMimeType;
	protected String mEncoderName;
	protected int mEncoderColorFormat;
	protected int mMaxFps = 0;

	/**
	 * Don't use this class directly.
	 * Uses CAMERA_FACING_BACK by default.
	 */
	public VideoStream(final long startTimeNs, final int videoEncoder) {
		super(startTimeNs);
		mMimeType = "video/avc";
		mMode = MODE_MEDIACODEC_API_2;
		mVideoEncoder = videoEncoder;
	}

	/**
	 * Sets the orientation of the preview.
	 *
	 * @param orientation The orientation of the preview
	 */
	public void setOrientation(int orientation) {
		mRequestedOrientation = orientation;
	}

	public int getOrientation() {
		return mRequestedOrientation;
	}

	/**
	 * Sets the configuration of the stream. You can call this method at any time
	 * and changes will take effect next time you call {@link #configure()}.
	 *
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(@NonNull final VideoQuality videoQuality) {
		if (!mRequestedQuality.equals(videoQuality)) {
			mRequestedQuality = videoQuality.clone();
		}
	}

	/**
	 * Returns the quality of the stream.
	 */
	@NonNull
	public VideoQuality getVideoQuality() {
		return mRequestedQuality;
	}

	/**
	 * Configures the stream. You need to call this before calling {@link #getSessionDescription()}
	 * to apply your configuration of the stream.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mMode = MODE_MEDIACODEC_API_2;
		mOrientation = mRequestedOrientation;
	}

	/**
	 * Starts the stream.
	 * This will also open the camera and display the preview
	 * if {@link #startPreview()} has not already been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		super.start();
		Log.d(TAG, "Stream configuration: FPS: " + mQuality.framerate + " Width: " + mQuality.resX + " Height: " + mQuality.resY);
	}

	/**
	 * Stops the stream.
	 */
	public synchronized void stop() {
		final VideoSource source = mSource;
		mSource = null;
		if (source != null) {
			if (mSurfaceId != 0) {
				source.removeSurface(mSurfaceId);
				mSurfaceId = 0;
			}
		}
		super.stop();
	}

	public synchronized void startPreview() {
	}

	/**
	 * Stops the preview.
	 */
	public synchronized void stopPreview() {
		stop();
	}

	public void setFactory(@NonNull final VideoSource.Factory factory) {
		mFactory = factory;
	}
//--------------------------------------------------------------------------------

	/**
	 * Video encoding is done by a MediaRecorder.
	 */
	protected void encodeWithMediaRecorder() throws IOException, ConfNotSupportedException {

		Log.d(TAG, "Video encoded using the MediaRecorder API");

		mSource = mFactory.createVideoSource(getVideoQuality());

		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		try {
			mMediaRecorder = new MediaRecorder();
			mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
			mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mMediaRecorder.setVideoEncoder(mVideoEncoder);
			mMediaRecorder.setVideoSize(mRequestedQuality.resX, mRequestedQuality.resY);
			mMediaRecorder.setVideoFrameRate(mRequestedQuality.framerate);

			// The bandwidth actually consumed is often above what was requested
			mMediaRecorder.setVideoEncodingBitRate((int) (mRequestedQuality.bitrate * 0.8));

			// We write the output of the camera in a local socket instead of a file !
			// This one little trick makes streaming feasible quiet simply: data from the camera
			// can then be manipulated at the other end of the socket
			FileDescriptor fd;
			if (sPipeApi == PIPE_API_PFD) {
				fd = mParcelWrite.getFileDescriptor();
			} else {
				fd = mSender.getFileDescriptor();
			}
			mMediaRecorder.setOutputFile(fd);
			mMediaRecorder.prepare();
			final Surface surface = mMediaRecorder.getSurface();
			if (surface != null) {
				mSurfaceId = mSource.addSurface(surface);
			}
			mMediaRecorder.start();
		} catch (Exception e) {
			throw new ConfNotSupportedException(e);
		}

		InputStream is;

		if (sPipeApi == PIPE_API_PFD) {
			is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);
		} else {
			is = mReceiver.getInputStream();
		}

		// This will skip the MPEG4 header if this step fails we can't stream anything :(
		try {
			byte[] buffer = new byte[4];
			// Skip all atoms preceding mdat atom
			while (!Thread.interrupted()) {
				while (is.read() != 'm') ;
				is.read(buffer, 0, 3);
				if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
			}
		} catch (IOException e) {
			Log.e(TAG, "Couldn't skip mp4 header :/");
			stop();
			throw e;
		}

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(is);
		mPacketizer.start();
	}

	/**
	 * Video encoding is done by a MediaCodec.
	 */
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {
		mSource = mFactory.createVideoSource(getVideoQuality());

		if (mMode == MODE_MEDIACODEC_API_2) {
			// Uses the method MediaCodec.createInputSurface to feed the encoder
			encodeWithMediaCodecMethod2();
		} else {
			throw new UnsupportedOperationException("only supports MODE_MEDIACODEC_API_2");
		}
	}

	/**
	 * Video encoding is done by a MediaCodec.
	 * But here we will use the buffer-to-surface method
	 */
	@SuppressLint({"InlinedApi", "NewApi"})
	protected void encodeWithMediaCodecMethod2() throws RuntimeException, IOException {

		Log.d(TAG, "Video encoded using the MediaCodec API with a surface");

		mMediaCodec = MediaCodec.createEncoderByType(mMimeType);
		final MediaFormat mediaFormat = MediaFormat.createVideoFormat(mMimeType, mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		final Surface surface = mMediaCodec.createInputSurface();
		mSurfaceId = mSource.addSurface(surface);
		mMediaCodec.start();
		MediaCodecUtils.dump(TAG + " RTSP", mediaFormat);
		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(MediaCodecInputStream.newInstance(mMediaCodec));
		mPacketizer.start();

	}

	/**
	 * Returns a description of the stream using SDP.
	 * This method can only be called after {@link Stream#configure()}.
	 *
	 * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
	 */
	public abstract String getSessionDescription() throws IllegalStateException;

}
