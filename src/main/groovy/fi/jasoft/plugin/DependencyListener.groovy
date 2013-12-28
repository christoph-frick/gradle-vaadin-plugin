/*
* Copyright 2013 John Ahlroos
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package fi.jasoft.plugin;

import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.Project;
import org.gradle.api.ProjectState;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.file.FileTree;

class DependencyListener implements ProjectEvaluationListener {

    void beforeEvaluate(Project project) {

        // Check to see if we are using the eclipse plugin instead of the eclipse-wtp plugin
        if (project.plugins.findPlugin('eclipse') && !project.plugins.findPlugin('eclipse-wtp')){
            project.getLogger().warn("You are using the eclipse plugin which does not support all " +
                    "features of the Vaadin plugin. Please use the eclipse-wtp plugin instead.")
        }
    }

    void afterEvaluate(Project project, ProjectState state) {

        if (!project.hasProperty('vaadin') || !project.vaadin.manageDependencies) {
            return
        }

        // Repositories
        addRepositories(project)

        createJetty8Configuration(project)

        def version = project.vaadin.version
        def gwtVersion = project.vaadin.gwt.version
        if (version.startsWith("6")) {
            createVaadin6Configuration(project, version, gwtVersion)
        } else {
            createVaadin7Configuration(project, version)
        }

        if (project.vaadin.testbench.enabled){
            createTestbenchConfiguration(project)
        }
    }

    private static void addRepositories(Project project) {

        def gradleVersion = Double.parseDouble(project.getGradle().gradleVersion);

        // Ensure maven central and maven local are included
        project.repositories.mavenCentral()
        project.repositories.mavenLocal()

        if (project.repositories.findByName('Vaadin addons') == null) {
            if (gradleVersion >= 1.9){
                project.repositories.maven({
                    name = 'Vaadin addons'
                    url = 'http://maven.vaadin.com/vaadin-addons'
                })
            }  else {
                project.repositories.mavenRepo(
                    name: 'Vaadin addons',
                    url: 'http://maven.vaadin.com/vaadin-addons')
            }

        }

        if (project.repositories.findByName('Vaadin snapshots') == null) {
            if (gradleVersion >= 1.9){
                project.repositories.maven({
                    name = 'Vaadin snapshots'
                    url = 'http://oss.sonatype.org/content/repositories/vaadin-snapshots'
                })
            } else {
                project.repositories.mavenRepo(
                    name: 'Vaadin snapshots',
                    url: 'http://oss.sonatype.org/content/repositories/vaadin-snapshots')
            }
        }

        if (project.repositories.findByName('Jasoft.fi Maven repository') == null) {
            if (gradleVersion >= 1.9){
                project.repositories.maven({
                    name = 'Jasoft.fi Maven repository'
                    url = 'http://mvn.jasoft.fi/maven2'
                })
            } else {
                project.repositories.mavenRepo(
                    name:  'Jasoft.fi Maven repository',
                    url: 'http://mvn.jasoft.fi/maven2')
            }
        }

        if (new File(GradleVaadinPlugin.getDebugDir()).exists()) {
            project.logger.lifecycle("Using development libs found at "+GradleVaadinPlugin.getDebugDir())
            project.repositories.flatDir(dirs: GradleVaadinPlugin.getDebugDir())
        }
    }

    private static void createJetty8Configuration(Project project){
        if(!project.configurations.hasProperty('jetty8')){
            project.configurations.create("jetty8")
            project.dependencies.add('jetty8', 'org.eclipse.jetty.aggregate:jetty-all-server:8.1.10.v20130312')
            project.dependencies.add('jetty8', 'fi.jasoft.plugin:gradle-vaadin-plugin:' + GradleVaadinPlugin.getVersion())
            project.dependencies.add('jetty8', 'asm:asm-all:3.3.1')
            project.dependencies.add('jetty8', 'javax.servlet.jsp:jsp-api:2.2')
        }
    }

    private static void createCommonVaadinConfiguration(Project project) {
        createGWTConfiguration(project)
        if(!project.configurations.hasProperty('vaadin')){
            project.configurations.create('vaadin')
            project.sourceSets.main.compileClasspath += project.configurations.vaadin
            project.sourceSets.test.compileClasspath += project.configurations.vaadin
            project.sourceSets.test.runtimeClasspath += project.configurations.vaadin

            // For servlet 3 support
            project.sourceSets.main.compileClasspath += project.configurations.jetty8
            project.sourceSets.test.compileClasspath += project.configurations.jetty8
            project.sourceSets.test.runtimeClasspath += project.configurations.jetty8

            project.war.classpath(project.configurations.vaadin)
        }
    }

    private static void createVaadin6Configuration(Project project, String version, String gwtVersion) {
        createCommonVaadinConfiguration(project)

        project.dependencies.add("vaadin", "com.vaadin:vaadin:${version}")
        if (project.vaadin.widgetset != null) {
            project.dependencies.add("vaadin-client", "com.google.gwt:gwt-user:" + gwtVersion)
            project.dependencies.add("vaadin-client", "com.google.gwt:gwt-dev:" + gwtVersion)
            project.dependencies.add("vaadin-client", "javax.validation:validation-api:1.0.0.GA")
        }
    }

    private static void createVaadin7Configuration(Project project, String version) {
        createCommonVaadinConfiguration(project)
        File webAppDir = project.convention.getPlugin(WarPluginConvention).webAppDir
        FileTree themes = project.fileTree(dir: webAppDir.canonicalPath + '/VAADIN/themes', include: '**/styles.scss')
        if (!themes.isEmpty()) {
            project.dependencies.add("vaadin", "com.vaadin:vaadin-theme-compiler:${version}")
        }

        if (project.vaadin.widgetset == null) {
            project.dependencies.add("vaadin", "com.vaadin:vaadin-client-compiled:${version}")
        } else {
            project.dependencies.add("vaadin-client", "com.vaadin:vaadin-client-compiler:${version}", {
                exclude([group: 'org.mortbay.jetty'])
            })
            project.dependencies.add("vaadin-client", "com.vaadin:vaadin-client:${version}")
            project.dependencies.add("vaadin-client", "javax.validation:validation-api:1.0.0.GA")
        }

        project.dependencies.add("vaadin", "com.vaadin:vaadin-server:${version}")
        project.dependencies.add("vaadin", "com.vaadin:vaadin-themes:${version}")

        if (Util.isPushSupportedAndEnabled(project)) {
            project.dependencies.add('vaadin', "com.vaadin:vaadin-push:${version}")
        }
    }

    private static void createGWTConfiguration(Project project){
        if(!project.configurations.hasProperty('vaadin-client')){
            project.configurations.create('vaadin-client')
            project.sourceSets.main.compileClasspath += project.configurations['vaadin-client']
            project.sourceSets.test.compileClasspath += project.configurations['vaadin-client']
            project.sourceSets.test.runtimeClasspath += project.configurations['vaadin-client']
        }
    }

    private static void createTestbenchConfiguration(Project project){
        if (!project.configurations.hasProperty('vaadin-testbench')){
            project.configurations.create('vaadin-testbench')
            project.dependencies.add('vaadin-testbench',"com.vaadin:vaadin-testbench:${project.vaadin.testbench.version}")
            project.sourceSets.test.compileClasspath += project.configurations['vaadin-testbench']
            project.sourceSets.test.runtimeClasspath += project.configurations['vaadin-testbench']
        }
    }
}