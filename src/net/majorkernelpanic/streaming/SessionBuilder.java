/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 * Copyright (c) 2014 - 2022 t_saki t_saki@serenegiant.com
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

package net.majorkernelpanic.streaming;

import net.majorkernelpanic.streaming.audio.AACStream;
import net.majorkernelpanic.streaming.audio.AMRNBStream;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.audio.IAudioStream;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.video.H263Stream;
import net.majorkernelpanic.streaming.video.H264Stream;
import net.majorkernelpanic.streaming.video.ILocalVideoStream;
import net.majorkernelpanic.streaming.video.IVideoStream;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoSource;

import android.content.Context;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.serenegiant.streaming.audio.AudioSource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Call {@link #getInstance()} to get access to the SessionBuilder.
 */
public class SessionBuilder implements Cloneable {

	private static final String TAG = SessionBuilder.class.getSimpleName();

	/** Can be used with {@link #setVideoEncoder}. */
	public final static int VIDEO_NONE = 0;

	/** Can be used with {@link #setVideoEncoder}. */
	public final static int VIDEO_H264 = 1;

	/** Can be used with {@link #setVideoEncoder}. */
	public final static int VIDEO_H263 = 2;

	/** Can be used with {@link #setAudioEncoder}. */
	public final static int AUDIO_NONE = 0;

	/** Can be used with {@link #setAudioEncoder}. */
	public final static int AUDIO_AMRNB = 3;

	/** Can be used with {@link #setAudioEncoder}. */
	public final static int AUDIO_AAC = 5;

	public static final int DESTINATION_PORT_VIDEO = 5006;
	public static final int DESTINATION_PORT_AUDIO = 5004;

	// Default configuration
	@NonNull
	private VideoQuality mVideoQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;
	@NonNull
	private AudioQuality mAudioQuality = AudioQuality.DEFAULT_AUDIO_QUALITY;
	@NonNull
	private final Bundle mSettings = new Bundle();
	@Nullable
	private VideoSource.Factory mVideoFactory = null;
	@Nullable
	private AudioSource.Factory mAudioFactory = null;
	private int mVideoEncoder = VIDEO_H263;
	private int mAudioEncoder = AUDIO_AMRNB;
	private int mCamera = CameraInfo.CAMERA_FACING_BACK;
	private int mTimeToLive = 64;
	private int mOrientation = 0;
	private boolean mFlash = false;
	private SurfaceView mSurfaceView = null;
	private String mOrigin = null;
	private String mDestination = null;
	private Session.Callback mCallback = null;
	private Session.Factory mSessionFactory = null;

	// The SessionManager implements the singleton pattern
	private static volatile SessionBuilder sInstance = null; 

	/**
	 * Returns a reference to the {@link SessionBuilder}.
	 * @return The reference to the {@link SessionBuilder}
	 */
	public static synchronized SessionBuilder getInstance() {
		if (sInstance == null) {
			SessionBuilder.sInstance = new SessionBuilder();
		}
		return sInstance;
	}	

	public static Session.Factory DefaultSessionFactory = new Session.Factory() {
		@NonNull
		@Override
		public Session createSession(@NonNull final Context context, @NonNull final SessionBuilder builder) {
			final Session session = new Session();
			session.setOrigin(builder.getOrigin());
			session.setDestination(builder.getDestination());
			session.setTimeToLive(builder.getTimeToLive());
			session.setCallback(builder.getCallback());

			switch (builder.getAudioEncoder()) {
			case AUDIO_AAC:
				final AACStream stream = new AACStream();
				session.addAudioTrack(stream);
				stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(context));
				break;
			case AUDIO_AMRNB:
				session.addAudioTrack(new AMRNBStream());
				break;
			}

			switch (builder.getVideoEncoder()) {
			case VIDEO_H263:
				session.addVideoTrack(new H263Stream(builder.getCamera()));
				break;
			case VIDEO_H264:
				final H264Stream stream = new H264Stream(builder.getCamera());
				stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(context));
				session.addVideoTrack(stream);
				break;
			}

			final IVideoStream video = session.getVideoTrack();
			if (video instanceof ILocalVideoStream) {
				((ILocalVideoStream)video).setFlashState(builder.getFlashState());
				((ILocalVideoStream)video).setSurfaceView(builder.getSurfaceView());
			}
			if (video != null) {
				video.setVideoQuality(builder.getVideoQuality());
				video.setOrientation(builder.getOrientation());
				video.setDestinationPorts(DESTINATION_PORT_VIDEO);
			}

			final IAudioStream audio = session.getAudioTrack();
			if (audio != null) {
				audio.setAudioQuality(builder.getAudioQuality());
				audio.setDestinationPorts(DESTINATION_PORT_AUDIO);
			}

			return session;
		}
	};

	// Removes the default public constructor
	protected SessionBuilder() {}

	@NonNull
	public Session build(@NonNull final Context context) {
		return mSessionFactory.createSession(context, this);
	}

	/** Sets the destination of the session. */
	public SessionBuilder setDestination(String destination) {
		mDestination = destination;
		return this; 
	}

	/** Sets the origin of the session. It appears in the SDP of the session. */
	public SessionBuilder setOrigin(String origin) {
		mOrigin = origin;
		return this;
	}

	/** Sets the video stream quality. */
	public SessionBuilder setVideoQuality(VideoQuality quality) {
		mVideoQuality = quality.clone();
		return this;
	}
	
	/** Sets the default video encoder. */
	public SessionBuilder setVideoEncoder(int encoder) {
		mVideoEncoder = encoder;
		return this;
	}

	public SessionBuilder setVideoFactory(final VideoSource.Factory factory) {
		mVideoFactory = factory;
		return this;
	}

	/** Sets the audio encoder. */
	public SessionBuilder setAudioEncoder(int encoder) {
		mAudioEncoder = encoder;
		return this;
	}
	
	/** Sets the audio quality. */
	public SessionBuilder setAudioQuality(AudioQuality quality) {
		mAudioQuality = quality.clone();
		return this;
	}

	public SessionBuilder setAudioFactory(final AudioSource.Factory factory) {
		mAudioFactory = factory;
		return this;
	}

	public SessionBuilder setFlashEnabled(boolean enabled) {
		mFlash = enabled;
		return this;
	}

	public SessionBuilder setCamera(int camera) {
		mCamera = camera;
		return this;
	}

	public SessionBuilder setTimeToLive(int ttl) {
		mTimeToLive = ttl;
		return this;
	}

	/** 
	 * Sets the SurfaceView required to preview the video stream. 
	 **/
	public SessionBuilder setSurfaceView(SurfaceView surfaceView) {
		mSurfaceView = surfaceView;
		return this;
	}

	public SessionBuilder setOrientation(final int orientation) {
		mOrientation = orientation;
		return this;
	}

	/** 
	 * Sets the orientation of the preview.
	 * @param orientation The orientation of the preview
	 */
	public SessionBuilder setPreviewOrientation(int orientation) {
		mOrientation = orientation;
		return this;
	}	
	
	public SessionBuilder setCallback(Session.Callback callback) {
		mCallback = callback;
		return this;
	}

	public SessionBuilder setSessionFactory(final Session.Factory factory) {
		mSessionFactory = factory;
		return this;
	}

	public SessionBuilder setSettings(@NonNull final Bundle settings) {
		mSettings.clear();
		mSettings.putAll(settings);
		return this;
	}

	/** Returns the destination ip address set with {@link #setDestination(String)}. */
	@Nullable
	public String getDestination() {
		return mDestination;
	}

	/** Returns the origin ip address set with {@link #setOrigin(String)}. */
	public String getOrigin() {
		return mOrigin;
	}

	/** Returns the audio encoder set with {@link #setAudioEncoder(int)}. */
	public int getAudioEncoder() {
		return mAudioEncoder;
	}

	/** Returns the id of the {@link android.hardware.Camera} set with {@link #setCamera(int)}. */
	public int getCamera() {
		return mCamera;
	}

	/** Returns the video encoder set with {@link #setVideoEncoder(int)}. */
	public int getVideoEncoder() {
		return mVideoEncoder;
	}

	public int getOrientation() {
		return mOrientation;
	}

	/** Returns the VideoQuality set with {@link #setVideoQuality(VideoQuality)}. */
	@NonNull
	public VideoQuality getVideoQuality() {
		return mVideoQuality;
	}

	@Nullable
	public VideoSource.Factory getVideoFactory() {
		return mVideoFactory;
	}

	/** Returns the AudioQuality set with {@link #setAudioQuality(AudioQuality)}. */
	@NonNull
	public AudioQuality getAudioQuality() {
		return mAudioQuality;
	}

	@Nullable
	public AudioSource.Factory getAudioFactory() {
		return mAudioFactory;
	}

	/** Returns the flash state set with {@link #setFlashEnabled(boolean)}. */
	public boolean getFlashState() {
		return mFlash;
	}

	/** Returns the SurfaceView set with {@link #setSurfaceView(SurfaceView)}. */
	@Nullable
	public SurfaceView getSurfaceView() {
		return mSurfaceView;
	}
	
	
	/** Returns the time to live set with {@link #setTimeToLive(int)}. */
	public int getTimeToLive() {
		return mTimeToLive;
	}

	@Nullable
	public Session.Callback getCallback() {
		return mCallback;
	}

	@Nullable
	public Session.Factory getSessionFactory() {
		return mSessionFactory;
	}

	@NonNull
	public Bundle getSettings() {
		return mSettings;
	}

	/** Returns a new {@link SessionBuilder} with the same configuration. */
	@NonNull
	public SessionBuilder clone() {
		final SessionBuilder result;
		try {
			result = (SessionBuilder) super.clone();
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		return result
		.setDestination(mDestination)
		.setOrigin(mOrigin)
		.setSurfaceView(mSurfaceView)
		.setOrientation(mOrientation)
		.setVideoQuality(mVideoQuality)
		.setVideoEncoder(mVideoEncoder)
		.setVideoFactory(mVideoFactory)
		.setFlashEnabled(mFlash)
		.setCamera(mCamera)
		.setTimeToLive(mTimeToLive)
		.setAudioEncoder(mAudioEncoder)
		.setAudioQuality(mAudioQuality)
		.setAudioFactory(mAudioFactory)
		.setCallback(mCallback)
		.setSettings(mSettings)
		.setSessionFactory(mSessionFactory);
	}

}
