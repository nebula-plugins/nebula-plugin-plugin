package nebula.plugin.plugin;

import org.gradle.api.Project;

/**
 * Shared code for applying archRules
 */
public class ArchRulesUtil {
    private ArchRulesUtil() {
    }

    static void setupArchRules(Project project) {
        String nebulaRulesVersion = "0.+";
        project.getPlugins().apply("com.netflix.nebula.archrules.runner");

        project.getDependencies().add("archRules", "com.netflix.nebula:archrules-deprecation:" + nebulaRulesVersion);

        //project.getDependencies().add("archRules", "com.netflix.nebula:archrules-joda:" + nebulaRulesVersion);
        //project.getDependencies().add("archRules", "com.netflix.nebula:archrules-testing-frameworks:" + nebulaRulesVersion);
    }
}
