/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 * Copyright (c) 2014 - 2022 t_saki t_saki@serenegiant.com
 *
 * created based on original MediaStream
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
 *
 */

package net.majorkernelpanic.streaming;

import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;
import net.majorkernelpanic.streaming.video.VideoStream;

/**
 * A MediaRecorder that streams what it records using a packetizer from the RTP package.
 * You can't use this class directly !
 */
public interface IMediaStream extends Stream {

	/** Raw audio/video will be encoded using the MediaRecorder API. */
	public static final byte MODE_MEDIARECORDER_API = 0x01;

	/** Raw audio/video will be encoded using the MediaCodec API with buffers. */
	public static final byte MODE_MEDIACODEC_API = 0x02;

	/** Raw audio/video will be encoded using the MediaCode API with a surface. */
	public static final byte MODE_MEDIACODEC_API_2 = 0x05;

	/**
	 * Sets the streaming method that will be used.
	 *
	 * If the mode is set to {@link #MODE_MEDIARECORDER_API}, raw audio/video will be encoded
	 * using the MediaRecorder API. <br />
	 *
	 * If the mode is set to {@link #MODE_MEDIACODEC_API} or to {@link #MODE_MEDIACODEC_API_2},
	 * audio/video will be encoded with using the MediaCodec. <br />
	 *
	 * The {@link #MODE_MEDIACODEC_API_2} mode only concerns {@link VideoStream}, it makes
	 * use of the createInputSurface() method of the MediaCodec API (Android 4.3 is needed there). <br />
	 *
	 * @param mode Can be {@link #MODE_MEDIARECORDER_API}, {@link #MODE_MEDIACODEC_API} or {@link #MODE_MEDIACODEC_API_2}
	 */
	public void setStreamingMethod(byte mode);
	/**
	 * Returns the streaming method in use, call this after
	 * {@link #configure()} to get an accurate response.
	 */
	public byte getStreamingMethod();
	/**
	 * Returns the packetizer associated with the {@link MediaStream}.
	 * @return The packetizer
	 */
	public AbstractPacketizer getPacketizer();
}
