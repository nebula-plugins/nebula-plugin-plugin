package nebula.plugin.plugin.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

class CreateQualifiedPluginPropertiesTask extends DefaultTask {
    private static Logger logger = Logging.getLogger(CreateQualifiedPluginPropertiesTask)

    @InputDirectory
    File pluginPropertiesDir

    @OutputDirectory
    File outputDir

    @TaskAction
    void create(IncrementalTaskInputs inputs) {
        /*if (!outputDir.exists()) {
            outputDir.mkdirs()
        }*/

        inputs.outOfDate { changed ->
            logger.debug("Changed: ${changed.file.name}")
            // nebula hardcoded as the prefix
            def target = new File(outputDir, "nebula.${changed.file.name}")
            target.text = changed.file.text
        }

        inputs.removed { changed ->
            logger.debug("Deleted: ${changed.file.name}")
            // nebula hardcoded as the prefix
            def target = new File(outputDir, "nebula.${changed.file.name}")
            target.delete()
        }
    }
}
