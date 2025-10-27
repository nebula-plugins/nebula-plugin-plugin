package nebula.plugin.plugin

//language=java
const val SAMPLE_JAVA_PLUGIN: String = """
package example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jspecify.annotations.NonNull;

/**
* 
*/
public class MyPlugin implements Plugin<@NonNull Project> {
    @Override
    public void apply(Project project) {
    }
}
"""

//language=java
const val SAMPLE_JAVA_MAIN_CLASS: String = """
package example;

/**
* 
*/
public class Main {
    /**
    * 
    * @param args
    */
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
"""