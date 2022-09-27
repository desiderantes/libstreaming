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

package net.majorkernelpanic.streaming.audio;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

import net.majorkernelpanic.streaming.MediaStream;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

/** 
 * Don't use this class directly.
 */
public abstract class AudioStream  extends MediaStream implements IAudioStream {

	private static final String TAG = AudioStream.class.getSimpleName();

	protected int mAudioSource;
	protected int mOutputFormat;
	protected int mAudioEncoder;
	@NonNull
	protected AudioQuality mRequestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
	@NonNull
	protected AudioQuality mQuality = mRequestedQuality.clone();
	
	public AudioStream() {
		setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
	}
	
	public void setAudioSource(int audioSource) {
		mAudioSource = audioSource;
	}

	@Override
	public void setAudioQuality(@NonNull final AudioQuality quality) {
		mRequestedQuality = quality;
	}
	
	/** 
	 * Returns the quality of the stream.  
	 */
	@NonNull
	@Override
	public AudioQuality getAudioQuality() {
		return mQuality;
	}	
	
	protected void setAudioEncoder(int audioEncoder) {
		mAudioEncoder = audioEncoder;
	}
	
	protected void setOutputFormat(int outputFormat) {
		mOutputFormat = outputFormat;
	}
	
	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		
		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		Log.v(TAG,"Requested audio with "+mQuality.bitRate/1000+"kbps"+" at "+mQuality.samplingRate/1000+"kHz");
		
		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setAudioSource(mAudioSource);
		mMediaRecorder.setOutputFormat(mOutputFormat);
		mMediaRecorder.setAudioEncoder(mAudioEncoder);
		mMediaRecorder.setAudioChannels(1);
		mMediaRecorder.setAudioSamplingRate(mQuality.samplingRate);
		mMediaRecorder.setAudioEncodingBitRate(mQuality.bitRate);
		
		// We write the output of the camera in a local socket instead of a file !			
		// This one little trick makes streaming feasible quiet simply: data from the camera
		// can then be manipulated at the other end of the socket
		FileDescriptor fd = null;
		if (sPipeApi == PIPE_API_PFD) {
			fd = mParcelWrite.getFileDescriptor();
		} else  {
			fd = mSender.getFileDescriptor();
		}
		mMediaRecorder.setOutputFile(fd);
		mMediaRecorder.setOutputFile(fd);

		mMediaRecorder.prepare();
		mMediaRecorder.start();

		InputStream is = null;
		
		if (sPipeApi == PIPE_API_PFD) {
			is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);
		} else  {
			try {
				// mReceiver.getInputStream contains the data from the camera
				is = mReceiver.getInputStream();
			} catch (IOException e) {
				stop();
				throw new IOException("Something happened with the local sockets :/ Start failed !");
			}
		}

		// the mPacketizer encapsulates this stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(is);
		mPacketizer.start();

	}
	
}
