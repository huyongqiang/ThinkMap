/*
 * Copyright 2014 Matthew Collins
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

package uk.co.thinkofdeath.thinkcraft.shared.platform.buffers;

public interface FloatBuffer extends Buffer {

    /**
     * Sets the value at the index
     *
     * @param index
     *         The index
     * @param value
     *         The value
     */
    void set(int index, float value);

    /**
     * Gets the value at the index
     *
     * @param index
     *         The index
     * @return The value
     */
    float get(int index);


}
