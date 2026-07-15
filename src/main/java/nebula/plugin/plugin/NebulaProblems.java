package nebula.plugin.plugin;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;

/**
 * Constants for Problem reporting
 */
public class NebulaProblems {
    public static final ProblemGroup NEBULA_GROUP =
            ProblemGroup.create("com.netflix.nebula", "Nebula Plugins");

    public static final ProblemId OSS_SETTINGS = ProblemId.create(
            "com.netflix.nebula.oss.settings not found",
            "using a nebula convention plugin without com.netflix.nebula.oss.settings is deprecated",
            NEBULA_GROUP);
}
