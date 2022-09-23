/*
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

package com.serenegiant.streaming;

import android.os.Bundle;

import androidx.annotation.NonNull;

public abstract class MediaStream extends net.majorkernelpanic.streaming.MediaStream {
   private static final boolean DEBUG = false;  // set false on production
   private static final String TAG = MediaStream.class.getSimpleName();

   @NonNull
   private final Bundle mSettings = new Bundle();

   /**
  	 * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called
  	 * @param settings The SharedPreferences that will be used to save SPS and PPS parameters
  	 */
  	public void setSettings(@NonNull final Bundle settings) {
  		mSettings.clear();
  		mSettings.putAll(settings);
  	}

  	@NonNull
  	public Bundle getSettings() {
  		return mSettings;
  	}

}
