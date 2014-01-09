package nebula.plugin.plugin

import com.google.common.base.Preconditions

import java.util.jar.JarFile
import java.util.jar.Manifest

/**
 * Inspect a class for certain meta-data.
 */
class ClassHelper {

    static String findSpecificationVersion(Class clazz) {
        def pkg = clazz.getPackage()
        pkg.specificationVersion?:null
    }

    static Manifest findManifest(Class clazz) {
        if (clazz.classLoader instanceof URLClassLoader) {
            try {
                return findManifestViaClassloader(clazz)
            } catch(FileNotFoundException nfe) {
                return findManifestViaJarFile(clazz)
            }
        } else {
            findManifestViaJarFile(clazz)
        }
    }

    static findManifestViaClassloader(Class clazz) {
        assert clazz.classLoader instanceof URLClassLoader
        URLClassLoader cl = (URLClassLoader) clazz.getClassLoader();
        URL url = cl.findResource("META-INF/MANIFEST.MF");
        Manifest manifest = new Manifest(url.openStream());
        return manifest
    }

    static findManifestViaJarFile(Class clazz) {
        final URL jarUrl = clazz.getProtectionDomain().getCodeSource().getLocation();
        final JarFile jf = new JarFile(new File(jarUrl.toURI()));
        return jf.getManifest()
    }

    static findManifestViaResource(Class clazz) {
        String className = clazz.getSimpleName() + ".class";
        String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            // Class not from JAR
            return null
        }
        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
        Manifest manifest = new Manifest(new URL(manifestPath).openStream());
        return manifest
    }

    static def findManifestValue(Class clazz, String key, Object defaultValue) {
        def manifest = findManifest(clazz)
        return (manifest && manifest.getEntries().containsKey(key))? manifest.getEntries().get(key) : defaultValue
    }
}
