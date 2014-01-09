package nebula.plugin.plugin

import com.google.common.io.Files
import org.apache.commons.lang.reflect.FieldUtils
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.internal.project.AbstractProject
import org.gradle.listener.BroadcastDispatch
import org.gradle.listener.ClosureBackedMethodInvocationDispatch
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.dispatch.MethodInvocation

/**
 * Utility methods to dive into Gradle internals, if needed.
 */
class GradleHelper {

    def AbstractProject project

    GradleHelper(AbstractProject project) {
        this.project = project
    }

    def static BroadcastDispatch<ProjectEvaluationListener> getProjectEvaluationListeners(AbstractProject project) {
        new GradleHelper(project).getProjectEvaluationListeners()
    }

    def BroadcastDispatch<ProjectEvaluationListener> getProjectEvaluationListeners() {
        ProjectEvaluationListener listener = project.getProjectEvaluationBroadcaster();

        def /* org.gradle.messaging.dispatch.ProxyDispatchAdapter.DispatchingInvocationHandler */ h = ((java.lang.reflect.Proxy) listener).h
        BroadcastDispatch<ProjectEvaluationListener> dispatcher = h.dispatch
        return dispatcher
    }

    def static beforeEvaluate(Project project, Closure beforeEvaluateClosure) {
        new GradleHelper(project).beforeEvaluate(beforeEvaluateClosure)
    }

    def beforeEvaluate(Closure beforeEvaluateClosure) {
        BroadcastDispatch<ProjectEvaluationListener> broadcast = getProjectEvaluationListeners( (AbstractProject) project)

        final String methodName = 'afterEvaluate'
        Dispatch<MethodInvocation> invocation =  new ClosureBackedMethodInvocationDispatch(methodName, beforeEvaluateClosure)

        Map<Object, Dispatch<MethodInvocation>> prependedHandlers = new LinkedHashMap<Object, Dispatch<MethodInvocation>>();
        prependedHandlers.put(invocation, invocation);
        prependedHandlers.putAll( broadcast.handlers )

        broadcast.handlers.clear()
        broadcast.handlers.putAll(prependedHandlers)
    }

    def getTempDir(String taskBaseName) {
        File tmpDir = new File(project.getBuildDir(), taskBaseName)
        Files.createParentDirs(tmpDir);
        tmpDir.mkdirs()
        return tmpDir
    }

    /**
     * Dig deeper into project object to see if group has been set. Wrap in beforeEvaluate if want to run later.
     * @param defaultGroup
     */
    def addDefaultGroup(String defaultGroup) {
        // Getting on AbstractProject will always feed out some group name if we're not at the root project, so look
        // past it's getGroup() method to see what's really set
        def directGroupName = FieldUtils.readField(project, 'group', true)
        if (!directGroupName) {
            project.logger.debug("Defaulting group to '${defaultGroup}', because direct group name ('${directGroupName}') is empty")
            project.group = defaultGroup
        }
        project.logger.info("Using group of ${project.group}")
    }
}
