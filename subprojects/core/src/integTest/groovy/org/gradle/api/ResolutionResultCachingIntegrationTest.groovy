/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

//resolution results are cached in process so the test only makes sense if gradle invocations share the process
@IgnoreIf({ !GradleContextualExecuter.longLivingProcess })
class ResolutionResultCachingIntegrationTest extends AbstractIntegrationSpec {

    /* notes

    - track the resolution time and the savings from using the feature
    - validate behavior for detached configurations
    - add new listener onBuildScriptClasspathChange
    - create org.gradle.daemon.caching.dependencyResolution flag
    - investigate and analyze locks used in DefaultLenientConfiguration

     */

    def setup() {
        executer.requireIsolatedDaemons()
        executer.withClassLoaderCaching(true)
        //executer.withResolutionResultCaching(true)
    }

    def "caches resolution results between build runs"() {
        buildFile << """
            repositories { jcenter() }
            configurations { conf }
            dependencies { conf 'org.mockito:mockito-core:1.10.18' }
            configurations.conf {
                incoming.beforeResolve { println "beforeResolve!!!" }
                incoming.afterResolve  { println "afterResolve!!!" }
            }
        """

        when: run "dependencies"

        then:
        output.contains "org.mockito:mockito-core:1.10.18"
        output.contains "beforeResolve!!!"
        output.contains "afterResolve!!!"

        when: run "dependencies"

        then:
        output.contains "org.mockito:mockito-core:1.10.18"
        !output.contains("beforeResolve!!!")
        !output.contains("afterResolve!!!")
    }

    def "resolves again when dependency changes between build runs"() {
        buildFile << """
            repositories { jcenter() }
            configurations { conf }
            dependencies { conf 'org.mockito:mockito-core:1.10.17' }
            configurations.conf {
                incoming.beforeResolve { println "beforeResolve!!!" }
                incoming.afterResolve  { println "afterResolve!!!" }
            }
        """

        when: run "dependencies"

        then:
        output.contains "org.mockito:mockito-core:1.10.17"
        output.contains "beforeResolve!!!"
        output.contains "afterResolve!!!"

        when:
        buildFile.text = """
            repositories { jcenter() }
            configurations { conf }
            dependencies { conf 'org.mockito:mockito-core:1.9.5' }
            configurations.conf {
                incoming.beforeResolve { println "beforeResolve!!!" }
                incoming.afterResolve  { println "afterResolve!!!" }
            }
        """

        run "dependencies"

        then:
        output.contains "org.mockito:mockito-core:1.9.5"
        output.contains("beforeResolve!!!")
        output.contains("afterResolve!!!")
    }
}