/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.*;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {
    private final TaskArtifactStateCacheAccess cacheAccess;
    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, TaskHistory> taskHistoryCache;
    private final TaskHistorySerializer serializer;
    private final StringInterner stringInterner;

    public CacheBackedTaskHistoryRepository(TaskArtifactStateCacheAccess cacheAccess, FileSnapshotRepository snapshotRepository, StringInterner stringInterner) {
        this.cacheAccess = cacheAccess;
        this.snapshotRepository = snapshotRepository;
        this.stringInterner = stringInterner;
        this.serializer = new TaskHistorySerializer(stringInterner);
        taskHistoryCache = cacheAccess.createCache("taskArtifacts", String.class, serializer);
    }

    public History getHistory(final TaskInternal task) {
        final TaskHistory history = loadHistory(task);
        final LazyTaskExecution currentExecution = new LazyTaskExecution();
        currentExecution.snapshotRepository = snapshotRepository;
        currentExecution.cacheAccess = cacheAccess;
        currentExecution.setOutputFileNameHashes(outputFilenameHashes(task));
        final LazyTaskExecution previousExecution = findPreviousExecution(currentExecution, history);
        if (previousExecution != null) {
            previousExecution.snapshotRepository = snapshotRepository;
            previousExecution.cacheAccess = cacheAccess;
        }

        return new History() {
            public TaskExecution getPreviousExecution() {
                return previousExecution;
            }

            public TaskExecution getCurrentExecution() {
                return currentExecution;
            }

            public void update() {
                cacheAccess.useCache("Update task history", new Runnable() {
                    public void run() {
                        history.configurations.add(0, currentExecution);
                        if (currentExecution.inputFilesSnapshotId == null && currentExecution.inputFilesSnapshot != null) {
                            currentExecution.inputFilesSnapshotId = snapshotRepository.add(currentExecution.inputFilesSnapshot);
                        }
                        if (currentExecution.outputFilesSnapshotId == null && currentExecution.outputFilesSnapshot != null) {
                            currentExecution.outputFilesSnapshotId = snapshotRepository.add(currentExecution.outputFilesSnapshot);
                        }
                        if (currentExecution.discoveredFilesSnapshotId == null && currentExecution.discoveredFilesSnapshot != null) {
                            currentExecution.discoveredFilesSnapshotId = snapshotRepository.add(currentExecution.discoveredFilesSnapshot);
                        }
                        while (history.configurations.size() > TaskHistory.MAX_HISTORY_ENTRIES) {
                            LazyTaskExecution execution = history.configurations.remove(history.configurations.size() - 1);
                            if (execution.inputFilesSnapshotId != null) {
                                snapshotRepository.remove(execution.inputFilesSnapshotId);
                            }
                            if (execution.outputFilesSnapshotId != null) {
                                snapshotRepository.remove(execution.outputFilesSnapshotId);
                            }
                            if (execution.discoveredFilesSnapshotId != null) {
                                snapshotRepository.remove(execution.discoveredFilesSnapshotId);
                            }
                        }
                        history.beforeSerialized();
                        taskHistoryCache.put(task.getPath(), history);
                    }
                });
            }
        };
    }

    private TaskHistory loadHistory(final TaskInternal task) {
        return cacheAccess.useCache("Load task history", new Factory<TaskHistory>() {
            public TaskHistory create() {
                ClassLoader original = serializer.getClassLoader();
                serializer.setClassLoader(task.getClass().getClassLoader());
                try {
                    TaskHistory history = taskHistoryCache.get(task.getPath());
                    return history == null ? new TaskHistory() : history;
                } finally {
                    serializer.setClassLoader(original);
                }
            }
        });
    }

    private Set<Integer> outputFilenameHashes(TaskInternal task) {
        Set<Integer> outputFilenameHashes = new HashSet<Integer>();
        HashFunction hashFunction = Hashing.murmur3_32();
        for (File file : task.getOutputs().getFiles()) {
            int hash = hashFunction.hashUnencodedChars(file.getAbsolutePath()).asInt();
            outputFilenameHashes.add(hash);
        }
        return outputFilenameHashes;
    }

    private LazyTaskExecution findPreviousExecution(TaskExecution currentExecution, TaskHistory history) {
        Set<Integer> outputFilenameHashes = currentExecution.getOutputFileNameHashes();
        LazyTaskExecution bestMatch = null;
        int bestMatchOverlap = 0;
        for (LazyTaskExecution configuration : history.configurations) {
            if (outputFilenameHashes.size() == 0) {
                if (configuration.getOutputFileNameHashes().size() == 0) {
                    bestMatch = configuration;
                    break;
                }
            }

            Set<Integer> intersection = new HashSet<Integer>(outputFilenameHashes);
            intersection.retainAll(configuration.getOutputFileNameHashes());
            if (intersection.size() > bestMatchOverlap) {
                bestMatch = configuration;
                bestMatchOverlap = intersection.size();
            }
            if (bestMatchOverlap == outputFilenameHashes.size()) {
                break;
            }
        }
        return bestMatch;
    }

    private static class TaskHistorySerializer implements Serializer<TaskHistory> {

        private ClassLoader classLoader;
        private final StringInterner stringInterner;

        public TaskHistorySerializer(StringInterner stringInterner) {
            this.stringInterner = stringInterner;
        }

        public TaskHistory read(Decoder decoder) throws Exception {
            byte executions = decoder.readByte();
            TaskHistory history = new TaskHistory();
            LazyTaskExecution.TaskHistorySerializer executionSerializer = new LazyTaskExecution.TaskHistorySerializer(classLoader, stringInterner);
            for (int i = 0; i < executions; i++) {
                LazyTaskExecution exec = executionSerializer.read(decoder);
                history.configurations.add(exec);
            }
            return history;
        }

        public void write(Encoder encoder, TaskHistory value) throws Exception {
            int size = value.configurations.size();
            encoder.writeByte((byte) size);
            LazyTaskExecution.TaskHistorySerializer executionSerializer = new LazyTaskExecution.TaskHistorySerializer(classLoader, stringInterner);
            for (LazyTaskExecution execution : value.configurations) {
                executionSerializer.write(encoder, execution);
            }
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }
    }

    private static class TaskHistory {
        private static final int MAX_HISTORY_ENTRIES = 3;
        private final List<LazyTaskExecution> configurations = new ArrayList<LazyTaskExecution>();
        public String toString() {
            return super.toString() + "[" + configurations.size() + "]";
        }

        public void beforeSerialized() {
            //cleaning up the transient fields, so that any in-memory caching is happy
            for (LazyTaskExecution c : configurations) {
                c.cacheAccess = null;
                c.snapshotRepository = null;
            }
        }
    }

    //TODO SF extract & unit test
    private static class LazyTaskExecution extends TaskExecution {
        private Long inputFilesSnapshotId;
        private Long outputFilesSnapshotId;
        private Long discoveredFilesSnapshotId;
        private transient FileSnapshotRepository snapshotRepository;
        private transient FileCollectionSnapshot inputFilesSnapshot;
        private transient FileCollectionSnapshot outputFilesSnapshot;
        private transient FileCollectionSnapshot discoveredFilesSnapshot;
        private transient TaskArtifactStateCacheAccess cacheAccess;

        @Override
        public FileCollectionSnapshot getInputFilesSnapshot() {
            if (inputFilesSnapshot == null) {
                inputFilesSnapshot = cacheAccess.useCache("fetch input files", new Factory<FileCollectionSnapshot>() {
                    public FileCollectionSnapshot create() {
                        return snapshotRepository.get(inputFilesSnapshotId);
                    }
                });
            }
            return inputFilesSnapshot;
        }

        @Override
        public void setInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot) {
            this.inputFilesSnapshot = inputFilesSnapshot;
            this.inputFilesSnapshotId = null;
        }

        @Override
        public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
            if (discoveredFilesSnapshot == null) {
                discoveredFilesSnapshot = cacheAccess.useCache("fetch discovered input files", new Factory<FileCollectionSnapshot>() {
                    public FileCollectionSnapshot create() {
                        return snapshotRepository.get(discoveredFilesSnapshotId);
                    }
                });
            }
            return discoveredFilesSnapshot;
        }

        @Override
        public void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot discoveredFilesSnapshot) {
            this.discoveredFilesSnapshot = discoveredFilesSnapshot;
            this.discoveredFilesSnapshotId = null;
        }

        @Override
        public FileCollectionSnapshot getOutputFilesSnapshot() {
            if (outputFilesSnapshot == null) {
                outputFilesSnapshot = cacheAccess.useCache("fetch output files", new Factory<FileCollectionSnapshot>() {
                    public FileCollectionSnapshot create() {
                        return snapshotRepository.get(outputFilesSnapshotId);
                    }
                });
            }
            return outputFilesSnapshot;
        }

        @Override
        public void setOutputFilesSnapshot(FileCollectionSnapshot outputFilesSnapshot) {
            this.outputFilesSnapshot = outputFilesSnapshot;
            outputFilesSnapshotId = null;
        }

        static class TaskHistorySerializer implements Serializer<LazyTaskExecution> {
            private final InputPropertiesSerializer inputPropertiesSerializer;
            private final StringInterner stringInterner;

            public TaskHistorySerializer(ClassLoader classLoader, StringInterner stringInterner) {
                this.inputPropertiesSerializer = new InputPropertiesSerializer(classLoader);
                this.stringInterner = stringInterner;
            }

            public LazyTaskExecution read(Decoder decoder) throws Exception {
                LazyTaskExecution execution = new LazyTaskExecution();
                execution.inputFilesSnapshotId = decoder.readLong();
                execution.outputFilesSnapshotId = decoder.readLong();
                execution.discoveredFilesSnapshotId = decoder.readLong();
                execution.setTaskClass(decoder.readString());
                int filenameHashCount = decoder.readInt();
                Set<Integer> filenameHashes = new HashSet<Integer>();
                for (int j = 0; j < filenameHashCount; j++) {
                    filenameHashes.add(decoder.readInt());
                }
                execution.setOutputFileNameHashes(filenameHashes);

                boolean inputProperties = decoder.readBoolean();
                if (inputProperties) {
                    Map<String, Object> map = inputPropertiesSerializer.read(decoder);
                    execution.setInputProperties(map);
                } else {
                    execution.setInputProperties(new HashMap<String, Object>());
                }
                return execution;
            }

            public void write(Encoder encoder, LazyTaskExecution execution) throws Exception {
                encoder.writeLong(execution.inputFilesSnapshotId);
                encoder.writeLong(execution.outputFilesSnapshotId);
                encoder.writeLong(execution.discoveredFilesSnapshotId);
                encoder.writeString(execution.getTaskClass());
                encoder.writeInt(execution.getOutputFileNameHashes().size());
                for (Integer outputFileNameHash : execution.getOutputFileNameHashes()) {
                    encoder.writeInt(outputFileNameHash);
                }
                if (execution.getInputProperties() == null || execution.getInputProperties().isEmpty()) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    inputPropertiesSerializer.write(encoder, execution.getInputProperties());
                }
            }
        }
    }
}
