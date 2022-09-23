/*
 * Copyright (c) 2014 - 2022 t_saki t_saki@serenegiant.com
 *
 * created based on original VideoStream
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
 *
 */

package net.majorkernelpanic.streaming.video;

import android.hardware.Camera.CameraInfo;

import net.majorkernelpanic.streaming.gl.SurfaceView;

import java.io.IOException;

public interface ILocalVideoStream extends IVideoStream {
	/**
	 * Sets the camera that will be used to capture video.
	 * You can call this method at any time and changes will take effect next time you start the stream.
	 *
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	public void setCamera(int camera);

	/**
	 * Switch between the front facing and the back facing camera of the phone.
	 * If {@link #startPreview()} has been called, the preview will be  briefly interrupted.
	 * If {@link #start()} has been called, the stream will be  briefly interrupted.
	 * You should not call this method from the main thread if you are already streaming.
	 *
	 * @throws IOException
	 * @throws RuntimeException
	 **/
	public void switchCamera() throws RuntimeException, IOException;

	/**
	 * Returns the id of the camera currently selected.
	 * Can be either {@link CameraInfo#CAMERA_FACING_BACK} or
	 * {@link CameraInfo#CAMERA_FACING_FRONT}.
	 */
	public int getCamera();

	/**
	 * Turns the LED on or off if phone has one.
	 */
	public void setFlashState(final boolean state);

	/**
	 * Toggles the LED of the phone if it has one.
	 * You can get the current state of the flash with {@link VideoStream#getFlashState()}.
	 */
	public void toggleFlash();

	/**
	 * Indicates whether or not the flash of the phone is on.
	 */
	public boolean getFlashState();

	/**
	 * Starts the preview.
	 */
	public void startPreview();

	/**
	 * Stops the preview.
	 */
	public void stopPreview();

	public void setSurfaceView(SurfaceView view);
}
