package nebula.plugin.plugin;

import org.gradle.api.Project;

import java.util.List;

/**
 * Shared code for applying archRules
 */
public class ArchRulesUtil {
    private ArchRulesUtil() {
    }

    static void setupArchRules(Project project) {
        if(!project.getName().equals("nebula-archrules-core")) { // avoid circular dependency
            String nebulaRulesVersion = "0.+";
            project.getPlugins().apply("com.netflix.nebula.archrules.runner");
            final var nebulaRules = List.of("archrules-deprecation", "archrules-joda", "archrules-testing-frameworks");
            nebulaRules.forEach(rule -> {
                if (!project.getName().equals(rule)) {
                    project.getDependencies().add("archRules", "com.netflix.nebula:" + rule + ":" + nebulaRulesVersion);
                }
            });
        }
    }
}
