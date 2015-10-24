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

import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.util.ChangeListener;

import java.util.Collections;
import java.util.Iterator;

/**
 * A rule which detects changes in the input files of a task.
 */
class InputFilesStateChangeRule {
    public static InputFilesTaskStateChanges create(final TaskExecution previousExecution, final TaskExecution currentExecution, final FileCollectionSnapshotter inputFilesSnapshotter, final FileCollectionSnapshot.PreCheck inputFilesPrecheckBefore) {
        return new InputFilesTaskStateChanges(previousExecution, currentExecution, inputFilesSnapshotter, inputFilesPrecheckBefore);
    }

    private static class ChangeListenerAdapter implements ChangeListener<String> {
        public InputFileChange lastChange;

        public void added(String fileName) {
            lastChange = new InputFileChange(fileName, ChangeType.ADDED);
        }

        public void removed(String fileName) {
            lastChange = new InputFileChange(fileName, ChangeType.REMOVED);
        }

        public void changed(String fileName) {
            lastChange = new InputFileChange(fileName, ChangeType.MODIFIED);
        }
    }

    static class InputFilesTaskStateChanges implements TaskStateChanges {
        private final TaskExecution previousExecution;
        private final TaskExecution currentExecution;
        private final FileCollectionSnapshotter inputFilesSnapshotter;
        private final FileCollectionSnapshot.PreCheck inputFilesPrecheckBefore;
        private FileCollectionSnapshot inputFilesSnapshot;

        private InputFilesTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, FileCollectionSnapshotter inputFilesSnapshotter, FileCollectionSnapshot.PreCheck inputFilesPrecheckBefore) {
            this.previousExecution = previousExecution;
            this.currentExecution = currentExecution;
            this.inputFilesSnapshotter = inputFilesSnapshotter;
            this.inputFilesPrecheckBefore = inputFilesPrecheckBefore;
        }

        public FileCollectionSnapshot getInputFilesSnapshot() {
            if (inputFilesSnapshot == null) {
                inputFilesSnapshot = inputFilesSnapshotter.snapshot(inputFilesPrecheckBefore);
            }
            return inputFilesSnapshot;
        }

        @Override
        public void snapshotBeforeTask() {
            getInputFilesSnapshot();
        }

        public Iterator<TaskStateChange> iterator() {
            if (previousExecution.getInputFilesSnapshot() == null) {
                return Collections.<TaskStateChange>singleton(new DescriptiveChange("Input file history is not available.")).iterator();
            }

            return new AbstractIterator<TaskStateChange>() {
                FileCollectionSnapshot.ChangeIterator<String> changeIterator;
                final ChangeListenerAdapter listenerAdapter = new ChangeListenerAdapter();
                int counter;

                @Override
                protected TaskStateChange computeNext() {
                    if (changeIterator == null) {
                        changeIterator = getInputFilesSnapshot().iterateChangesSince(previousExecution.getInputFilesSnapshot());
                    }
                    if (changeIterator.next(listenerAdapter)) {
                        counter++;
                        return listenerAdapter.lastChange;
                    }
                    if(counter == 0 && previousExecution != null && previousExecution.getInputFilesHash() != null) {
                        previousExecution.setInputFilesHash(inputFilesPrecheckBefore.getHash());
                    }
                    return endOfData();
                }
            };
        }

        public void snapshotAfterTask() {
            currentExecution.setInputFilesHash(inputFilesPrecheckBefore.getHash());
            currentExecution.setInputFilesSnapshot(getInputFilesSnapshot());
        }
    }
}
