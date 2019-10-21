package com.github.triplet.gradle.play.tasks

import com.github.triplet.gradle.androidpublisher.PlayPublisher
import com.github.triplet.gradle.common.utils.marked
import com.github.triplet.gradle.play.PlayPublisherExtension
import com.github.triplet.gradle.play.tasks.internal.EditTaskBase
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

internal abstract class CommitEdit @Inject constructor(
        extension: PlayPublisherExtension
) : EditTaskBase(extension) {
    @TaskAction
    fun commit() {
        val file = editIdFile.get().asFile

        if (project.gradle.taskGraph.allTasks.any { it.state.failure != null }) {
            println("Build failed, skipping")
            file.reset()
            return
        }

        project.serviceOf<WorkerExecutor>().noIsolation().submit(Committer::class) {
            config.set(extension.serializableConfig)
            editIdFile.set(file)
        }
    }

    abstract class Committer : WorkAction<Committer.Params> {
        private val file = parameters.editIdFile.get().asFile
        private val appId = file.nameWithoutExtension
        private val publisher = PlayPublisher(
                parameters.config.get().serviceAccountCredentials!!,
                parameters.config.get().serviceAccountEmail,
                appId
        )

        override fun execute() {
            if (file.marked("commit").exists()) {
                println("Committing changes")
                try {
                    publisher.commitEdit(file.readText())
                } finally {
                    file.reset()
                }
            } else if (file.marked("skipped").exists()) {
                println("Changes pending commit")
            } else {
                Logging.getLogger(CommitEdit::class.java).info("Nothing to commit, skipping")
            }
        }

        interface Params : WorkParameters {
            val config: Property<PlayPublisherExtension.Config>
            val editIdFile: RegularFileProperty
        }
    }
}
