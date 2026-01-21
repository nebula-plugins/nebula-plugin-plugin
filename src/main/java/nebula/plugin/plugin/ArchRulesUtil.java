package nebula.plugin.plugin;

import org.gradle.api.Project;

import java.util.List;
import java.util.Set;

/**
 * Shared code for applying archRules
 */
public class ArchRulesUtil {
    private ArchRulesUtil() {
    }

    static void setupArchRules(Project project) {
        // avoid circular dependencies in core libraries
        if (!Set.of("nebula-archrules-core", "archrules-common").contains(project.getName())) {
            String nebulaRulesVersion = "0.+";
            project.getPlugins().apply("com.netflix.nebula.archrules.runner");
            final var nebulaRules = List.of(
                    "archrules-deprecation",
                    "archrules-guava",
                    "archrules-javax",
                    "archrules-joda",
                    "archrules-testing-frameworks",
                    "archrules-nullability",
                    "archrules-security"
            );
            nebulaRules.forEach(rule -> {
                if (!project.getName().equals(rule)) {
                    project.getDependencies().add("archRules", "com.netflix.nebula:" + rule + ":" + nebulaRulesVersion);
                }
            });
        }
    }
}
