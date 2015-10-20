/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.Iterators;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.*;
import org.gradle.internal.Factory;

import java.util.Iterator;

/**
 * Represents the complete changes in a tasks state
 */
public class TaskUpToDateState {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 3;

    private TaskStateChanges noHistoryState;
    private InputFilesStateChangeRule.InputFilesTaskStateChanges directInputFilesState;
    private TaskStateChanges inputFilesState;
    private TaskStateChanges inputPropertiesState;
    private TaskStateChanges taskTypeState;
    private TaskStateChanges outputFilesState;
    private SummaryTaskStateChanges allTaskChanges;
    private SummaryTaskStateChanges rebuildChanges;

    public TaskUpToDateState(final TaskInternal task, TaskHistoryRepository.History history, final FileCollectionSnapshotter outputFilesSnapshotter, final FileCollectionSnapshotter inputFilesSnapshotter) {
        final TaskExecution thisExecution = history.getCurrentExecution();
        final TaskExecution lastExecution = history.getPreviousExecution();

        noHistoryState = NoHistoryStateChangeRule.create(task, lastExecution);
        taskTypeState = TaskTypeStateChangeRule.create(task, lastExecution, thisExecution);
        inputPropertiesState = InputPropertiesStateChangeRule.create(task, lastExecution, thisExecution);
        createOutputFilesState(task, outputFilesSnapshotter, thisExecution, lastExecution);
        createInputFilesState(task, inputFilesSnapshotter, thisExecution, lastExecution);

        allTaskChanges = new SummaryTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, noHistoryState, taskTypeState, inputPropertiesState, outputFilesState, inputFilesState);
        rebuildChanges = new SummaryTaskStateChanges(1, noHistoryState, taskTypeState, inputPropertiesState, outputFilesState);
    }

    private void createInputFilesState(TaskInternal task, final FileCollectionSnapshotter inputFilesSnapshotter, final TaskExecution thisExecution, final TaskExecution lastExecution) {
        // Capture inputs state
        try {
            final FileCollectionSnapshot.PreCheck inputFilesPrecheckBefore = inputFilesSnapshotter.preCheck(task.getInputs().getFiles());
            final Factory<InputFilesStateChangeRule.InputFilesTaskStateChanges> directInputFilesStateFactory = new Factory<InputFilesStateChangeRule.InputFilesTaskStateChanges>() {
                @Override
                public InputFilesStateChangeRule.InputFilesTaskStateChanges create() {
                    return InputFilesStateChangeRule.create(lastExecution, thisExecution, inputFilesSnapshotter, inputFilesPrecheckBefore);
                }
            };
            final Factory<TaskStateChanges> inputFilesStateFactory = new Factory<TaskStateChanges>() {
                @Override
                public TaskStateChanges create() {
                    directInputFilesState = directInputFilesStateFactory.create();
                    return caching(directInputFilesState);
                }
            };
            if (lastExecution != null && lastExecution.getInputFilesHash() != null && lastExecution.getInputFilesHash().equals(inputFilesPrecheckBefore.getHash())) {
                inputFilesState = new LazyNoChangesTaskStateChanges(inputFilesStateFactory);
            } else {
                inputFilesState = inputFilesStateFactory.create();
            }
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of input files for task '%s' during up-to-date check.  See stacktrace for details.", task.getName()), e);
        }
    }

    private void createOutputFilesState(final TaskInternal task, final FileCollectionSnapshotter outputFilesSnapshotter, final TaskExecution thisExecution, final TaskExecution lastExecution) {
        // Capture outputs state
        try {
            final FileCollectionSnapshot.PreCheck outputFilesPrecheckBefore = outputFilesSnapshotter.preCheck(task.getOutputs().getFiles());
            final Factory<TaskStateChanges> outputFilesStateFactory = new Factory<TaskStateChanges>() {
                @Override
                public TaskStateChanges create() {
                    return caching(OutputFilesStateChangeRule.create(task, lastExecution, thisExecution, outputFilesSnapshotter, outputFilesPrecheckBefore));
                }
            };
            if (lastExecution != null && lastExecution.getOutputFilesHash() != null && lastExecution.getOutputFilesHash().equals(outputFilesPrecheckBefore.getHash())) {
                outputFilesState = new LazyNoChangesTaskStateChanges(outputFilesStateFactory);
            } else {
                outputFilesState = outputFilesStateFactory.create();
            }
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of output files for task '%s' during up-to-date check.  See stacktrace for details.", task.getName()), e);
        }
    }

    private TaskStateChanges caching(TaskStateChanges wrapped) {
        return new CachingTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    public TaskStateChanges getInputFilesChanges() {
        return inputFilesState;
    }

    public TaskStateChanges getAllTaskChanges() {
        return allTaskChanges;
    }

    public TaskStateChanges getRebuildChanges() {
        return rebuildChanges;
    }

    public FilesSnapshotSet getInputFilesSnapshot() {
        if (directInputFilesState == null) {
            inputFilesState.snapshotBeforeTask();
        }
        return directInputFilesState.getInputFilesSnapshot().getSnapshot();
    }

    private static class LazyNoChangesTaskStateChanges implements TaskStateChanges {
        private final Factory<TaskStateChanges> stateChangesFactory;
        TaskStateChanges delegate;

        protected LazyNoChangesTaskStateChanges(Factory<TaskStateChanges> stateChangesFactory) {
            this.stateChangesFactory = stateChangesFactory;
        }

        @Override
        public void snapshotBeforeTask() {
            getDelegate().snapshotBeforeTask();
        }

        private TaskStateChanges getDelegate() {
            if (delegate == null) {
                delegate = stateChangesFactory.create();
            }
            return delegate;
        }

        @Override
        public void snapshotAfterTask() {
            getDelegate().snapshotAfterTask();
        }

        @Override
        public Iterator<TaskStateChange> iterator() {
            return Iterators.emptyIterator();
        }
    }
}
