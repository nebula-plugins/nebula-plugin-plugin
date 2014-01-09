package nebula.plugin.plugin

import org.apache.commons.lang.reflect.FieldUtils
import org.gradle.api.Action
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionExecuter
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.nativeplatform.filesystem.FileSystems
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator

/**
 * Created with IntelliJ IDEA.
 * User: jryan
 * Date: 1/6/14
 * Time: 9:56 AM
 * To change this template use File | Settings | File Templates.
 */
class CopySpecHelper {

    static visitCopySpec(CopySpecInternal copySpec, Closure closure) {
        Instantiator instantiator = new DirectInstantiator()
        //FileSystem fileSystem = new GenericFileSystem(new EmptyChmod(), new FallbackStat(), new FallbackSymlink())
        org.gradle.internal.nativeplatform.filesystem.FileSystem fileSystem = FileSystems.getDefault()
        CopyActionExecuter copyActionExecuter = new CopyActionExecuter(instantiator, fileSystem);
        copyActionExecuter.execute(copySpec, new CopyAction() {
            @Override
            WorkResult execute(CopyActionProcessingStream stream) {
                stream.process(new CopyActionProcessingStreamAction() {
                    @Override
                    void processFile(FileCopyDetailsInternal details) {
                        closure.call(copySpec, details)
                    }
                })
            }
        })
    }

    static void visitAllCopySpecs(CopySpecInternal delegateCopySpec, Closure closure) {
        delegateCopySpec.walk(new Action<CopySpecInternal>() {
            @Override
            void execute(CopySpecInternal csi) {
                // TODO Try to search for core spec, e.g. dig deeper into delegating copy specs
                if (csi instanceof DestinationRootCopySpec) {
                    csi = FieldUtils.readField(csi, 'delegate', true)
                }

                visitCopySpec(csi, closure)
            }
        })
    }

    static CopySpecInternal findCopySpec(CopySpecInternal delegateCopySpec, Closure<Boolean> closure) {
        def foundCsi = null
        visitAllCopySpecs(delegateCopySpec) { CopySpecInternal csi, FileCopyDetailsInternal details ->
            if(foundCsi==null && closure.call(csi, details)) {
                foundCsi = csi
            }
        }
        return foundCsi
    }
}
