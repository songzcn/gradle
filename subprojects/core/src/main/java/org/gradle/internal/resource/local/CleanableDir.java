/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.resource.local;

import org.gradle.cache.CleanableStore;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class CleanableDir implements CleanableStore {
    private final String displayName;
    private final File dir;

    public CleanableDir(String displayName, File dir) {
        this.displayName = displayName;
        this.dir = dir;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public File getBaseDir() {
        return dir;
    }

    @Override
    public Collection<File> getReservedCacheFiles() {
        return Collections.emptySet();
    }
}
