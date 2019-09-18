import groovy.transform.CompileStatic
import org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder

CompilerCustomizationBuilder.withConfig(configuration) {
    ast(CompileStatic)
}