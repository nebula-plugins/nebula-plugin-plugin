package nebula.plugin.plugin

import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class NebulaRootPluginTest {
    @Test
    fun `plugin sets group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(NebulaRootPlugin::class.java)
        assertThat(project.group).isEqualTo("com.netflix.nebula")
    }
}