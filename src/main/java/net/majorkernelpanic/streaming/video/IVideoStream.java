/*
 * Copyright (c) 2014 - 2022 t_saki t_saki@serenegiant.com
 *
 * created based on original VideoStream
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

package net.majorkernelpanic.streaming.video;

import net.majorkernelpanic.streaming.IMediaStream;

import androidx.annotation.NonNull;

public interface IVideoStream extends IMediaStream {
	/**
	 * Sets the configuration of the stream. You can call this method at any time
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
    void setVideoQuality(@NonNull final VideoQuality videoQuality);

	/**
	 * Returns the quality of the stream.
	 */
	@NonNull
    VideoQuality getVideoQuality();

	void setOrientation(final int orientation);

	int getOrientation();
}
