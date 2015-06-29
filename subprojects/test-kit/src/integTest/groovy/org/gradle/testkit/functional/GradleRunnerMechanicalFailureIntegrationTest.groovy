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

package org.gradle.testkit.functional

import org.gradle.util.GFileUtils
import org.gradle.util.TextUtil

import static org.gradle.testkit.functional.TaskResult.*

class GradleRunnerMechanicalFailureIntegrationTest extends AbstractGradleRunnerIntegrationTest {
    def "build execution for script with invalid Groovy syntax"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    'Hello world!"
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.buildAndFail()

        then:
        noExceptionThrown()
        !result.standardOutput.contains(':helloWorld')
        result.standardError.contains('Could not compile build file')
        result.tasks.empty
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UPTODATE).empty
        result.taskPaths(FAILED).empty
    }

    def "build execution for script with unknown Gradle API method class"() {
        given:
        buildFile << """
            task helloWorld {
                doSomething {
                    println 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.buildAndFail()

        then:
        noExceptionThrown()
        !result.standardOutput.contains(':helloWorld')
        result.standardError.contains('Could not find method doSomething()')
        result.tasks.empty
        result.taskPaths(SUCCESS).empty
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UPTODATE).empty
        result.taskPaths(FAILED).empty
    }

    def "build execution with badly formed argument"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.arguments << '--unknown'
        gradleRunner.build()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Reason:
Unknown command-line option '--unknown'.""")
        message.contains('Problem configuring task :helloWorld from command line.')
    }

    def "build execution with non-existent working directory"() {
        given:
        File nonExistentWorkingDir = new File('some/path/that/does/not/exist')
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.withWorkingDir(nonExistentWorkingDir)
        gradleRunner.build()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Reason:
Project directory '$nonExistentWorkingDir.absolutePath' does not exist.""")
        !message.contains(':helloWorld')
    }

    def "build execution with invalid JVM arguments"() {
        given:
        GFileUtils.writeFile('org.gradle.jvmargs=-unknown', testProjectDir.file('gradle.properties'))
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.build()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Reason:
Unable to start the daemon process.
This problem might be caused by incorrect configuration of the daemon.
For example, an unrecognized jvm option is used.""")
        !message.contains(':helloWorld')
    }

    def "daemon dies during build execution"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                    Runtime.runtime.halt(0)
                    println 'Bye world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.build()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Output:
:helloWorld
Hello world!""")
        !message.contains('Bye world!')
        message.contains("""Reason:
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
""")
    }
}