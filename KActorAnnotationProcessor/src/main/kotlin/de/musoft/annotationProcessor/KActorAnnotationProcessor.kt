package de.musoft.annotationProcessor

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic.Kind.ERROR

private const val ACTOR_CLASS_SUFFIX = "Actor"
private const val MESSAGE_CLASS_SUFFIX = "Msg"
private const val CONTEXT_VAR_NAME = "context"
private const val ACTOR_PROP_NAME = "actor"
private const val ACTORFACTORY_FUNC_NAME = "actorFactory"

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("de.musoft.annotationProcessor.KActor")
@SupportedOptions(KActorAnnotationProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class KActorAnnotationProcessor : AbstractProcessor() {
    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(KActor::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
            processingEnv.messager.printMessage(ERROR, "Can't find the target directory for generated Kotlin files.")
            return false
        }

        for (annotatedElement: Element in annotatedElements) {
            val annotatedTypeElement = annotatedElement as? TypeElement ?: continue
            val annotatedClassName = annotatedTypeElement.asClassName()
            val packageName = annotatedClassName.packageName()
            val actorClassName = ClassName(packageName, annotatedClassName.simpleName() + ACTOR_CLASS_SUFFIX)

            val fileSpec = FileSpec.builder(packageName, actorClassName.simpleName())
                    .addStaticImport("kotlinx.coroutines.experimental.channels", "actor")
                    .addStaticImport("kotlinx.coroutines.experimental.channels", "Channel")
                    .addType(actorClassSpec(annotatedTypeElement, actorClassName))
                    .build()
            fileSpec.writeTo(File(kaptKotlinGeneratedDir).toPath())
        }

        return true
    }

    private fun messageBaseClassSpec(visibleMethodElements: List<ExecutableElement>, messageBaseClassName: ClassName): TypeSpec {

        val actorMessageSpecs = messageTypeSpecs(visibleMethodElements, messageBaseClassName)

        return TypeSpec.classBuilder(messageBaseClassName)
                .addModifiers(KModifier.SEALED, KModifier.PRIVATE)
                .addTypes(actorMessageSpecs)
                .build()
    }

    private fun actorClassSpec(annotatedTypeElement: TypeElement, actorClassName: ClassName): TypeSpec {
        val contextTypeClassName = ClassName("kotlin.coroutines.experimental", "CoroutineContext")
        val visibleMethodElements = annotatedTypeElement.enclosedElements
                .filterNot { it.modifiers.contains(Modifier.PRIVATE) }
                .filterNot { it.modifiers.contains(Modifier.PROTECTED) }
                .mapNotNull { it as? ExecutableElement }
                .filter { it.kind == ElementKind.METHOD }
        val contextParameterSpec = ParameterSpec
                .builder(CONTEXT_VAR_NAME, contextTypeClassName)
                .defaultValue("kotlinx.coroutines.experimental.DefaultDispatcher")
                .build()
        val visibleDelegateConstructorElements = annotatedTypeElement.enclosedElements
                .filterNot { it.modifiers.contains(Modifier.PRIVATE) }
                .mapNotNull { it as? ExecutableElement }
                .filter { it.kind == ElementKind.CONSTRUCTOR }
        val visibleDelegateConstructorsParameters = visibleDelegateConstructorElements.map { delegateConstructorElement ->
            delegateConstructorElement.parameters
        }
        val delegateConstructorsParameterSpecs = visibleDelegateConstructorsParameters.map { visibleDelegateConstructorParameters ->
            visibleDelegateConstructorParameters.map { visibleDelegateConstructorParameter ->
                ParameterSpec.builder(visibleDelegateConstructorParameter.simpleName.toString(), visibleDelegateConstructorParameter.asType().asTypeName())
                        .build()
            }
        }
        val constructorSpecs = delegateConstructorsParameterSpecs
                .map { constructorParameterSpecs ->
                    val constructorParameterList = constructorParameterSpecs.joinToString { it.name }

                    FunSpec.constructorBuilder()
                            .addParameters(constructorParameterSpecs)
                            .addParameter(contextParameterSpec)
                            .callSuperConstructor(constructorParameterList)
                            .addCode("this.$ACTOR_PROP_NAME = $ACTORFACTORY_FUNC_NAME($CONTEXT_VAR_NAME)\n")
                            .build()
                }
        val messageBaseClassName = actorClassName.nestedClass(annotatedTypeElement.simpleName.toString() + MESSAGE_CLASS_SUFFIX)
        val messageBaseClassSpec = messageBaseClassSpec(visibleMethodElements, messageBaseClassName)
        val actorPropertySpec = actorPropertySpec(messageBaseClassName)
        val actorFactoryFunSpec = actorFactoryFunSpec(visibleMethodElements, messageBaseClassName)

        val delegateMethodSpecs = delegateMethodSpecs(visibleMethodElements, messageBaseClassName)

        return TypeSpec.classBuilder(actorClassName)
                .superclass(annotatedTypeElement.asClassName())
                .addFunctions(constructorSpecs)
                .addType(messageBaseClassSpec)
                .addProperty(actorPropertySpec)
                .addFunction(actorFactoryFunSpec)
                .addFunctions(delegateMethodSpecs)
                .build()
    }

    private fun actorFactoryFunSpec(visibleMethodElements: List<ExecutableElement>, messageBaseClassName: ClassName): FunSpec {
        val actorInitializerFragments = mutableListOf(
                """ |return actor($CONTEXT_VAR_NAME, Channel.UNLIMITED) {
                    |  for (msg in channel) {
                    |    when (msg) {
                    |""".trimMargin()
        )

        actorInitializerFragments.addAll(messageHandlerFragments(visibleMethodElements, messageBaseClassName))

        actorInitializerFragments.add(
                """ |    }
                    |  }
                    |}
                    |""".trimMargin()
        )

        val actorInitializer = actorInitializerFragments.joinToString("")
        val actorClassName = ParameterizedTypeName.get(ClassName("kotlinx.coroutines.experimental.channels", "ActorJob"), messageBaseClassName)
        val contextTypeClassName = ClassName("kotlin.coroutines.experimental", "CoroutineContext")
        val contextParamSpec = ParameterSpec
                .builder(CONTEXT_VAR_NAME, contextTypeClassName)
                .build()
        return FunSpec.builder(ACTORFACTORY_FUNC_NAME)
                .addModifiers(KModifier.PRIVATE)
                .addParameter(contextParamSpec)
                .returns(actorClassName)
                .addCode(actorInitializer)
                .build()
    }

    private fun actorPropertySpec(messageBaseClassName: ClassName): PropertySpec {
        val actorClassName = ParameterizedTypeName.get(ClassName("kotlinx.coroutines.experimental.channels", "ActorJob"), messageBaseClassName)
        return PropertySpec
                .builder(ACTOR_PROP_NAME, actorClassName, KModifier.PRIVATE)
                .build()
    }

    private fun messageHandlerFragments(visibleMethodElements: List<ExecutableElement>, messageBaseClassName: ClassName) = visibleMethodElements.map { method ->
        val delegateMethodName = method.simpleName.toString()
        val messageName = messageBaseClassName.simpleName() + "." + delegateMethodName.capitalize()
        val parameterList = method.parameters
                .joinToString { parameter ->
                    "msg." + parameter.simpleName
                }

        """ |      is $messageName -> {
            |        try {
            |          super.$delegateMethodName($parameterList)
            |        } catch (t: Throwable) {
            |          val currentThread = Thread.currentThread()
            |          currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, t)
            |        }
            |      }
            |""".trimMargin()
    }

    private fun delegateMethodSpecs(visibleMethodElements: List<ExecutableElement>, messageBaseClassName: ClassName) = visibleMethodElements.map { method ->
        val delegateMethodName = method.simpleName.toString()
        val parameterSpecs = method.parameters
                .map { parameter ->
                    val parameterSimpleName = parameter.simpleName.toString()
                    ParameterSpec.builder(parameterSimpleName, parameter.asType().asTypeName())
                            .build()
                }
        val messageParameterList = method.parameters.joinToString { parameter -> parameter.simpleName }
        val messageName = messageBaseClassName.simpleName() + "." + delegateMethodName.capitalize()
        FunSpec.builder(method.simpleName.toString())
                .addModifiers(KModifier.OVERRIDE)
                .addParameters(parameterSpecs)
                .returns(UNIT)
                .addCode("actor.offer($messageName($messageParameterList))\n")
                .build()
    }

    private fun messageTypeSpecs(visibleMethodElements: List<ExecutableElement>, baseClassName: ClassName) = visibleMethodElements.map { method ->
        val methodName = method.simpleName.toString()
        val methodParameters = method.parameters
        val constructorParametersFromMethodSpec = methodParameters.toParameterSpecList()
        val constructorSpec = FunSpec.constructorBuilder()
                .addParameters(constructorParametersFromMethodSpec)
                .build()
        val propertiesSpec = methodParameters.toPropertySpecList()

        TypeSpec.classBuilder(methodName.capitalize())
                .superclass(baseClassName)
                .primaryConstructor(constructorSpec)
                .addProperties(propertiesSpec)
                .build()
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
