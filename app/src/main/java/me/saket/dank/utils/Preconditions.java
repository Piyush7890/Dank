/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.saket.dank.utils;

import android.os.Looper;
import android.support.annotation.Nullable;

import io.reactivex.Observer;

public final class Preconditions {
  public static <T> T checkNotNull(@Nullable T value, String message) {
    if (value == null) {
      throw new NullPointerException(message);
    }
    return value;
  }

  public static boolean checkMainThread(Observer<?> observer) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      observer.onError(new IllegalStateException(
          "Expected to be called on the main thread but was " + Thread.currentThread().getName()));
      return false;
    }
    return true;
  }

  private Preconditions() {}
}
