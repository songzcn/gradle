/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.Transformer;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FixedAgeOldestCacheCleanup;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.resource.local.TouchingFileAccessTracker;
import org.gradle.util.CollectionUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.singleton;
import static org.gradle.cache.internal.AbstractCacheCleanup.DIRECT_CHILDREN;
import static org.gradle.cache.internal.FixedAgeOldestCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {
    private final PersistentCache cache;
    private final Transformer<File, File> jarFileTransformer;

    public DefaultCachedClasspathTransformer(CacheRepository cacheRepository, JarCache jarCache, List<CachedJarFileStore> fileStores) {
        this.cache = cacheRepository
            .cache("jars-3")
            .withDisplayName("jars")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withCleanup(new FixedAgeOldestCacheCleanup(DIRECT_CHILDREN, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
            .open();
        this.jarFileTransformer = new TouchingJarFileTransformer(new CachedJarFileTransformer(jarCache, fileStores));
    }

    @Override
    public ClassPath transform(ClassPath classPath) {
        return DefaultClassPath.of(CollectionUtils.collect(classPath.getAsFiles(), jarFileTransformer));
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls) {
        return CollectionUtils.collect(urls, new Transformer<URL, URL>() {
            @Override
            public URL transform(URL url) {
                if (url.getProtocol().equals("file")) {
                    try {
                        return jarFileTransformer.transform(new File(url.toURI())).toURI().toURL();
                    } catch (URISyntaxException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    } catch (MalformedURLException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                } else {
                    return url;
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    private class CachedJarFileTransformer implements Transformer<File, File> {
        private final JarCache jarCache;
        private final Factory<File> baseDir;
        private final List<String> prefixes;

        CachedJarFileTransformer(JarCache jarCache, List<CachedJarFileStore> fileStores) {
            this.jarCache = jarCache;
            baseDir = Factories.constant(cache.getBaseDir());
            prefixes = new ArrayList<String>(fileStores.size() + 1);
            prefixes.add(directoryPrefix(cache.getBaseDir()));
            for (CachedJarFileStore fileStore : fileStores) {
                for (File rootDir : fileStore.getFileStoreRoots()) {
                    prefixes.add(directoryPrefix(rootDir));
                }
            }
        }

        private String directoryPrefix(File dir) {
            return dir.getAbsolutePath() + File.separator;
        }

        @Override
        public File transform(final File original) {
            if (shouldUseFromCache(original)) {
                return cache.useCache(new Factory<File>() {
                    public File create() {
                        return jarCache.getCachedJar(original, baseDir);
                    }
                });
            }
            return original;
        }

        private boolean shouldUseFromCache(File original) {
            if (!original.isFile()) {
                return false;
            }
            String absolutePath = original.getAbsolutePath();
            for (String prefix : prefixes) {
                if (absolutePath.startsWith(prefix)) {
                    return false;
                }
            }
            return true;
        }
    }

    private class TouchingJarFileTransformer implements Transformer<File, File> {

        private final Transformer<File, File> delegate;
        private final FileAccessTracker fileAccessTracker;

        TouchingJarFileTransformer(Transformer<File, File> delegate) {
            this.delegate = delegate;
            this.fileAccessTracker = new TouchingFileAccessTracker(cache.getBaseDir(), 1);
        }

        @Override
        public File transform(File file) {
            File result = delegate.transform(file);
            fileAccessTracker.markAccessed(singleton(result));
            return result;
        }
    }
}
