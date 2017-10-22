package de.musoft.annotationProcessor

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic.Kind.ERROR

private const val ACTOR_CLASS_POSTFIX = "Actor"
private const val MESSAGE_CLASS_POSTFIX = "Msg"
private const val CONTEXT_PROP_NAME = "context"
private const val RESPONES_PROP_NAME = "response"

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

        for (element: Element in annotatedElements) {
            val typeElement = element as? TypeElement ?: continue
            val packageElement = typeElement.enclosingElement as? PackageElement ?: continue
            val packageName = packageElement.toString()
            val actorClassName = ClassName(packageName, typeElement.simpleName.toString() + ACTOR_CLASS_POSTFIX)

            val fileSpec = FileSpec.get(packageName, actorClassSpec(typeElement, actorClassName))
            fileSpec.writeTo(File(kaptKotlinGeneratedDir).toPath())
        }

        return true
    }

    private fun messageBaseClassSpec(typeElement: TypeElement, messageBaseClassName: ClassName): TypeSpec {

        val visibleMethodElements = typeElement.enclosedElements
                .filter { it.kind == ElementKind.METHOD }
                .filter { !it.modifiers.contains(Modifier.PRIVATE) }
        val actorMessageSpecs = messageTypeSpecs(visibleMethodElements, messageBaseClassName)


        return TypeSpec.classBuilder(messageBaseClassName)
                .addModifiers(KModifier.SEALED, KModifier.PRIVATE)
                .addTypes(actorMessageSpecs)
                .build()
    }

    private fun actorClassSpec(typeElement: TypeElement, actorClassName: ClassName): TypeSpec {
        val contextTypeClassName = ClassName("kotlinx.coroutines.experimental", "CoroutineDispatcher")
        val contextParameterSpec = ParameterSpec
                .builder(CONTEXT_PROP_NAME, contextTypeClassName)
                .defaultValue("kotlinx.coroutines.experimental.DefaultDispatcher")
                .build()
        val constructorSpec = FunSpec.constructorBuilder()
                .addParameter(contextParameterSpec)
                .build()
        val contextPropertySpec = PropertySpec
                .builder(CONTEXT_PROP_NAME, contextTypeClassName)
                .addModifiers(KModifier.PRIVATE)
                .initializer(CONTEXT_PROP_NAME)
                .build()
        val messageBaseClassName = actorClassName.nestedClass(typeElement.simpleName.toString() + MESSAGE_CLASS_POSTFIX)
        val messageBaseClassSpec = messageBaseClassSpec(typeElement, messageBaseClassName)

        return TypeSpec.classBuilder(actorClassName)
                .primaryConstructor(constructorSpec)
                .addProperty(contextPropertySpec)
                .addType(messageBaseClassSpec)
                .build()
    }

    private fun messageTypeSpecs(methodElements: List<Element>, baseClassName: ClassName): List<TypeSpec> {
        return methodElements.map { method ->
            method as ExecutableElement
            val methodName = method.simpleName.toString()
            val methodParameters = method.parameters
            val responseClassName = ParameterizedTypeName.get(
                    ClassName("kotlinx.coroutines.experimental", "CompletableDeferred"),
                    method.returnType.asTypeName()).asNullable()
            val constructorResponseParameterSpec = ParameterSpec
                    .builder(RESPONES_PROP_NAME, responseClassName)
                    .build()
            val constructorParametersFromMethodSpec = methodParameters.toParameterSpecList()
            val constructorSpec = FunSpec.constructorBuilder()
                    .addParameter(constructorResponseParameterSpec)
                    .addParameters(constructorParametersFromMethodSpec)
                    .build()
            val responsePropertySpec = PropertySpec
                    .builder(RESPONES_PROP_NAME, responseClassName)
                    .initializer(RESPONES_PROP_NAME)
                    .build()
            val propertiesSpec = methodParameters.toPropertySpecList()

            TypeSpec.classBuilder(methodName.capitalize())
                    .superclass(baseClassName)
                    .primaryConstructor(constructorSpec)
                    .addProperty(responsePropertySpec)
                    .addProperties(propertiesSpec)
                    .build()
        }
    }

    private fun VariableElement.toParameterSpecList(): ParameterSpec {
        val parameterName = simpleName.toString()
        val parameterTypeName = asType().asTypeName()
        return ParameterSpec.builder(parameterName, parameterTypeName).build()
    }

    private fun List<VariableElement>.toParameterSpecList() = map { parameter ->
        parameter.toParameterSpecList()
    }

    private fun VariableElement.toPropertySpecList(): PropertySpec {
        val parameterName = simpleName.toString()
        val parameterTypeName = asType().asTypeName()
        return PropertySpec.builder(parameterName, parameterTypeName).initializer(parameterName).build()
    }

    private fun List<VariableElement>.toPropertySpecList() = map { parameter ->
        parameter.toPropertySpecList()
    }
}
