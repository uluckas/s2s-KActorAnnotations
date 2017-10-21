package de.musoft.annotationProcessor

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic.Kind.*

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("de.musoft.annotationProcessor.KActorAnnotation")
@SupportedOptions(KActorAnnotationProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class KActorAnnotationProcessor : AbstractProcessor() {
    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(KActorAnnotation::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
            processingEnv.messager.printMessage(ERROR, "Can't find the target directory for generated Kotlin files.")
            return false
        }

        //processingEnv.messager.printMessage(ERROR, "Processing annotations $annotations")
        for (element : Element in annotatedElements) {
            //processingEnv.messager.printMessage(ERROR, "Processing element $element")
            val typeElement = element as? TypeElement ?: continue
            val packageElement = typeElement.enclosingElement as? PackageElement ?: continue
            val packageName = packageElement.toString()
            val actorClassName = typeElement.simpleName.toString() + "Actor"
            val messageBaseClassName = actorClassName + "Message"

            val fileSpecBuilder = FileSpec.builder(packageName, actorClassName)

            // KotlinPoet actor message base class
            fileSpecBuilder.addType(
                    messageBaseClassSpec(messageBaseClassName)
            )

            val visibleMethodElements = typeElement.enclosedElements
                    .filter { it.kind == ElementKind.METHOD}
                    .filter { !it.modifiers.contains(Modifier.PRIVATE) }

            val actorMessageSpecs = messageTypeSpecs(visibleMethodElements, actorClassName, packageName, messageBaseClassName)

            actorMessageSpecs.forEach {
                fileSpecBuilder.addType(it)
            }

            fileSpecBuilder.addType(
                    actorClassSpec(actorClassName)
            )

            var file = File(kaptKotlinGeneratedDir, actorClassName)
            file.parentFile.mkdirs()
            fileSpecBuilder.build().writeTo(file)
        }

        return true
    }

    private fun messageBaseClassSpec(messageBaseClassName: String): TypeSpec {
        return TypeSpec.classBuilder(messageBaseClassName)
                .addModifiers(KModifier.SEALED)
                .build()
    }

    private fun actorClassSpec(className: String) =
            TypeSpec.classBuilder(className)
                    .build()

    private fun messageTypeSpecs(methodElements: List<Element>, className: String, packageName: String, baseClassName: String): List<TypeSpec> {
        return methodElements.map { method ->
            method as ExecutableElement
            val methodName = method.simpleName.toString()
            val parameters = method.parameters
            val constructorPropertiesSpec = parameters.map { parameter ->
                val parameterName = parameter.simpleName.toString()
                val parameterTypeName = ClassName(parameter)
                ParameterSpec.builder(parameterName, parameterTypeName).build()
            }
            val constructorSpec = FunSpec.constructorBuilder().addParameters(constructorPropertiesSpec).build()

            TypeSpec.classBuilder(className + methodName.capitalize() + "Message")
                    .superclass(ClassName(packageName, baseClassName))
                    .primaryConstructor(constructorSpec)
                    .build()
        }
    }

    operator fun ClassName.Companion.invoke(element: Element) : ClassName {
        val parentNames = mutableListOf<String>()
        val topName = element.simpleName.toString()
        var parent = element.enclosingElement
        while (parent.kind != ElementKind.PACKAGE) {
            val parentName = parent.simpleName.toString()
            processingEnv.messager.printMessage(ERROR, "$parentName is of kind ${parent.kind}")
            parentNames.add(parentName)
            parent = element.enclosingElement
        }
        val packageName = parent.simpleName.toString()

        return ClassName(packageName, topName, *parentNames.toTypedArray())
    }
}