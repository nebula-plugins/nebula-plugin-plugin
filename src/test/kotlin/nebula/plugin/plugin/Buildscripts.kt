package nebula.plugin.plugin

//language=kotlin
const val DISABLE_PUBLISH_TASKS : String = """
afterEvaluate {
    tasks.withType<AbstractPublishToMaven>() {
        onlyIf { false }
    }
    tasks.withType<Sign>(){
        onlyIf { false } // we don't have a signing key in integration tests (yet)
    }
}
"""


//language=kotlin
const val DISABLE_MAVEN_CENTRAL_TASKS : String = """
project.tasks.findByName("initializeSonatypeStagingRepository")?.onlyIf { false }
project.tasks.findByName("closeSonatypeStagingRepository")?.onlyIf { false }
project.tasks.findByName("releaseSonatypeStagingRepository")?.onlyIf { false }
"""