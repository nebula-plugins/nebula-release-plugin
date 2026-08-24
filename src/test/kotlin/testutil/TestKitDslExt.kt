package testutil

import nebula.test.dsl.NebulaTestKitDsl
import nebula.test.dsl.ProjectBuilder

@NebulaTestKitDsl
class ReleaseExtensionDsl {
    val lines = mutableSetOf<String>()

    fun versionStrategy(init: String){
        lines.add("versionStrategy($init)")
    }

    fun build(): String{
        return if(lines.isNotEmpty()){
            """
    release {
        ${lines.joinToString("\n")}
    }
    """
        } else ""
    }

}
fun ProjectBuilder.release(dsl: ReleaseExtensionDsl.() -> Unit) {
    rawBuildScript(ReleaseExtensionDsl().apply(dsl).build())
}