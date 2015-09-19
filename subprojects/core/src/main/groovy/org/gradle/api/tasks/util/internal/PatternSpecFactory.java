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

package org.gradle.api.tasks.util.internal;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.RelativePathSpec;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class PatternSpecFactory {
    private static final int RESULTS_CACHE_MAX_SIZE = 5000000;
    private static final int INSTANCES_MAX_SIZE = 50000;
    private final Cache<CacheKey, Boolean> specResultCache;
    private final Cache<SpecKey, Spec> specInstanceCache;

    public PatternSpecFactory() {
        specResultCache = CacheBuilder.newBuilder().maximumSize(RESULTS_CACHE_MAX_SIZE).build();
        specInstanceCache = CacheBuilder.newBuilder().maximumSize(INSTANCES_MAX_SIZE).build();
    }

    public Spec<FileTreeElement> createSpec(boolean caching, PatternSet patternSet) {
        Spec<FileTreeElement> includePart = createIncludeSpec(caching, patternSet);
        Spec<FileTreeElement> excludePart = createExcludeSpec(caching, patternSet);
        return Specs.and(includePart, Specs.not(excludePart));
    }

    public Spec<FileTreeElement> createIncludeSpec(boolean caching, PatternSet patternSet) {
        List<Spec<FileTreeElement>> allIncludeSpecs = Lists.newArrayList();
        if (patternSet.getIncludes().size() > 0) {
            allIncludeSpecs.add(createSpec(caching, patternSet.getIncludes(), true, patternSet.isCaseSensitive()));
        }
        allIncludeSpecs.addAll(patternSet.getIncludeSpecs());
        return Specs.or(true, allIncludeSpecs);
    }

    public Spec<FileTreeElement> createExcludeSpec(boolean caching, PatternSet patternSet) {
        List<Spec<FileTreeElement>> allExcludeSpecs = Lists.newArrayList();
        if (patternSet.getExcludes().size() > 0) {
            allExcludeSpecs.add(createSpec(caching, patternSet.getExcludes(), false, patternSet.isCaseSensitive()));
        }
        allExcludeSpecs.add(createSpec(caching, ImmutableList.copyOf(DirectoryScanner.getDefaultExcludes()), false, patternSet.isCaseSensitive()));
        allExcludeSpecs.addAll(patternSet.getExcludeSpecs());
        return Specs.or(false, allExcludeSpecs);
    }

    public Spec<FileTreeElement> createSpec(boolean caching, Collection<String> patterns, boolean include, boolean caseSensitive) {
        if (caching) {
            return createCachingSpec(patterns, include, caseSensitive);
        } else {
            return createActualSpec(patterns, include, caseSensitive);
        }
    }

    private Spec<FileTreeElement> createActualSpec(Collection<String> patterns, boolean include, boolean caseSensitive) {
        List<Spec<FileTreeElement>> matchers = Lists.newArrayList();
        for (String pattern : patterns) {
            Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(include, caseSensitive, pattern);
            matchers.add(new RelativePathSpec(patternMatcher));
        }
        return Specs.or(include, matchers);
    }

    private Spec<FileTreeElement> createCachingSpec(final Collection<String> patterns, final boolean include, final boolean caseSensitive) {
        final SpecKey key = new SpecKey(ImmutableList.copyOf(patterns), include, caseSensitive);
        try {
            return Cast.uncheckedCast(specInstanceCache.get(key, new Callable<Spec<FileTreeElement>>() {
                @Override
                public Spec<FileTreeElement> call() throws Exception {
                    Spec<FileTreeElement> spec = createActualSpec(patterns, include, caseSensitive);
                    return new CachingSpec(key, spec);
                }
            }));
        } catch (ExecutionException e) {
            UncheckedException.throwAsUncheckedException(e);
            return null;
        }
    }

    private class CachingSpec implements Spec<FileTreeElement> {
        private final SpecKey key;
        private final Spec<FileTreeElement> spec;

        CachingSpec(SpecKey key, Spec<FileTreeElement> spec) {
            this.key = key;
            this.spec = spec;
        }

        @Override
        public boolean isSatisfiedBy(final FileTreeElement element) {
            CacheKey cacheKey = new CacheKey(element.getRelativePath(), key);
            try {
                return specResultCache.get(cacheKey, new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return spec.isSatisfiedBy(element);
                    }
                });
            } catch (ExecutionException e) {
                UncheckedException.throwAsUncheckedException(e);
                return false;
            }
        }
    }

    private static class CacheKey {
        private final RelativePath relativePath;
        private final SpecKey specKey;


        private CacheKey(RelativePath relativePath, SpecKey specKey) {
            this.relativePath = relativePath;
            this.specKey = specKey;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey that = (CacheKey) o;

            return Objects.equal(this.relativePath, that.relativePath)
                && Objects.equal(this.specKey, that.specKey);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(relativePath, specKey);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("relativePath", relativePath)
                .add("specKey", specKey)
                .toString();
        }
    }

    private static class SpecKey {
        private final ImmutableList<String> patterns;
        private final boolean include;
        private final boolean caseSensitive;

        private SpecKey(ImmutableList<String> patterns, boolean include, boolean caseSensitive) {
            this.patterns = patterns;
            this.include = include;
            this.caseSensitive = caseSensitive;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SpecKey that = (SpecKey) o;

            return Objects.equal(this.patterns, that.patterns)
                && Objects.equal(this.include, that.include)
                && Objects.equal(this.caseSensitive, that.caseSensitive);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(patterns, include, caseSensitive);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("patterns", patterns)
                .add("include", include)
                .add("caseSensitive", caseSensitive)
                .toString();
        }
    }
}
