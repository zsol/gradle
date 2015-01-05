/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.configurations;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultProjectDataCache<T> implements ProjectDataCache<T> {

    private final Object lock = new Object();
    Map<String, T> cache = new HashMap<String, T>();

    public void buildScriptClasspathChanged(File buildScript) {
        synchronized (lock) {
            cache.clear();
        }
    }

    public T get(String identifier) {
        synchronized (lock) {
            return cache.get(identifier);
        }
    }

    public void put(String identifier, T value) {
        synchronized (lock) {
            cache.put(identifier, value);
        }
    }
}