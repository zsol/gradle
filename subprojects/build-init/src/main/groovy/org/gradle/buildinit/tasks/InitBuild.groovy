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

package org.gradle.buildinit.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.internal.tasks.options.Option
import org.gradle.api.internal.tasks.options.OptionValues
import org.gradle.api.tasks.TaskAction
import org.gradle.buildinit.plugins.internal.BuildInitModifier
import org.gradle.buildinit.plugins.internal.BuildInitTypeIds
import org.gradle.buildinit.plugins.internal.ProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry

import static org.gradle.buildinit.plugins.internal.BuildInitModifier.NONE

/**
 * Generates a Gradle project structure.
  */
@Incubating
class InitBuild extends DefaultTask {
    private String type
    private String with

    ProjectLayoutSetupRegistry projectLayoutRegistry

    /**
     * The desired type of build to create, defaults to {@value BuildInitTypeIds#POM} if 'pom.xml' is found in project root
     * if no pom.xml is found, it defaults to {@value BuildInitTypeIds#BASIC}.
     *
     * This property can be set via command-line option '--type'.
     */
    String getType() {
        type ?: project.file("pom.xml").exists() ? BuildInitTypeIds.POM : BuildInitTypeIds.BASIC
    }

    /**
     * Modifier options that influence how a type of build is created
     *
     * This property can be set via command-line option '--with'
     */
    String getWith() {
        with
    }

    ProjectLayoutSetupRegistry getProjectLayoutRegistry() {
        if (projectLayoutRegistry == null) {
            projectLayoutRegistry = services.get(ProjectLayoutSetupRegistry)
        }
        return projectLayoutRegistry
    }

    @TaskAction
    void setupProjectLayout() {
        def type = getType()
        def modifier = BuildInitModifier.fromName(getWith())
        def projectLayoutRegistry = getProjectLayoutRegistry()
        if (!projectLayoutRegistry.supports(type)) {
            throw new GradleException("The requested build setup type '${type}' is not supported. Supported types: ${projectLayoutRegistry.supportedTypes.collect{"'$it'"}.sort().join(", ")}.")
        }
        ProjectInitDescriptor initDescriptor = (ProjectInitDescriptor) projectLayoutRegistry.get(type)
        if (modifier != NONE && !initDescriptor.supports(modifier)) {
            throw new GradleException("The requested init modifier '" + modifier.getId() + "' is not supported in '" + type + "' setup type");
        }
        initDescriptor.generate(modifier)
    }

    @Option(option = "type", description = "Set type of build to create.")
    public void setType(String type) {
        this.type = type;
    }

    @OptionValues("type")
    List<String> getAvailableBuildTypes(){
        return getProjectLayoutRegistry().getSupportedTypes();
    }

    @Option(option = "with", description = "Set modifiers for how a type of build is created.")
    public void setWith(String with) {
        this.with = with
    }

    @OptionValues("with")
    List<String> getAvailableModifiers() {
        return BuildInitModifier.listSupported();
    }
}
