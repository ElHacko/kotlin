/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import java.io.File

// You can see "How To" via link: https://jetbrains.quip.com/xQ2WAUy9bZmy/How-to-use-AggregateTest-task
open class AggregateTest : Test() { // Inherit from Test to see test results in IDEA Test viewer
    private var patterns: MutableMap<String, MutableList<String>> = mutableMapOf<String, MutableList<String>>()

    // Stubs to avoid exceptions when initializing a base 'Test' class
    init {
        binaryResultsDirectory.convention(project.layout.buildDirectory.dir("stub"))
        classpath = project.files("stub")
        testClassesDirs = project.files("stub")
    }

    fun configure(testPatternPath: String) {
        initPatterns(testPatternPath)

        project.gradle.taskGraph.whenReady {
            allTasks.filterIsInstance<Test>().forEach { testTask -> subTaskConfigure(testTask) }

            if (!project.gradle.startParameter.taskNames.all { project.tasks.findByPath(it) is AggregateTest }) {
                logger.warn("Please, don't use AggregateTest and non-AggregateTest test tasks together. You can get incorrect results.")
            }
        }
    }

    private fun initPatterns(testPatternPath: String) {
        if (!File(testPatternPath).exists())
            return
        File(testPatternPath)
            .readLines()
            .asSequence()
            .filter { it.isNotEmpty() }
            .forEach { line ->
                // patternType is exclude or include value
                val (pattern, patternType) = line.split(',').map { it.trim() }
                patterns.getOrPut(patternType) { mutableListOf() }.add(pattern)
            }
    }

    private fun subTaskConfigure(testTask: Test) {
        testTask.outputs.upToDateWhen { false }
        testTask.ignoreFailures = true

        testTask.filter {
            isFailOnNoMatchingTests = false
            patterns["include"]?.let {
                it.forEach { pattern ->
                    includeTestsMatching(pattern)
                }
            }
            patterns["exclude"]?.let {
                it.forEach { pattern ->
                    excludeTestsMatching(pattern)
                }
            }
        }
    }

    @Override
    @TaskAction
    override fun executeTests() {
        // Do nothing
    }
}