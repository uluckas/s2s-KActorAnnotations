package de.musoft.annotationProcessor

import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic.Kind.ERROR

private const val ACTOR_CLASS_SUFFIX = "Actor"
private const val MESSAGE_CLASS_SUFFIX = "Msg"
private const val CONTEXT_PROP_NAME = "context"
private const val DELEGATE_PROP_NAME = "delegate"
private const val ACTOR_PROP_NAME = "actor"
private const val RESPONES_PROP_NAME = "response"
private const val FIREANDFORGET_METHOD_SUFFIX = "AndForget"
private const val ASYNC_METHOD_SUFFIX = "Async"
private const val RESPONSE_PARAMETER_NAME = "response"

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
        val contextTypeClassName = ClassName("kotlinx.coroutines.experimental", "CoroutineDispatcher")
        val visibleMethodElements = annotatedTypeElement.enclosedElements
                .filterNot { it.modifiers.contains(Modifier.PRIVATE) }
                .filterNot { it.modifiers.contains(Modifier.PROTECTED) }
                .mapNotNull { it as? ExecutableElement }
                .filter { it.kind == ElementKind.METHOD }
        val contextParameterSpec = ParameterSpec
                .builder(CONTEXT_PROP_NAME, contextTypeClassName)
                .defaultValue("kotlinx.coroutines.experimental.DefaultDispatcher")
                .build()
        val delegateParameterSpec = ParameterSpec
                .builder(DELEGATE_PROP_NAME, annotatedTypeElement.asClassName())
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
        val constructorSpecs = delegateConstructorsParameterSpecs.map { constructorParameterSpecs ->
            val parameterValues = constructorParameterSpecs
                    .joinToString() { constructorParameterSpec ->
                        constructorParameterSpec.name
                    }
            FunSpec.constructorBuilder()
                    .addParameters(constructorParameterSpecs)
                    .addParameter(contextParameterSpec)
                    .callThisConstructor("${annotatedTypeElement.simpleName}($parameterValues), $CONTEXT_PROP_NAME")
                    .build()
        }
        val primaryConstructorSpec = FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter(delegateParameterSpec)
                .addParameter(contextParameterSpec)
                .build()
        val messageBaseClassName = actorClassName.nestedClass(annotatedTypeElement.simpleName.toString() + MESSAGE_CLASS_SUFFIX)
        val messageBaseClassSpec = messageBaseClassSpec(visibleMethodElements, messageBaseClassName)
        val actorPropertySpec = actorPropertySpec(visibleMethodElements, messageBaseClassName)

        val delegateMethodSpecs = delegteMethodSpecs(visibleMethodElements, messageBaseClassName)

        return TypeSpec.classBuilder(actorClassName)
                .primaryConstructor(primaryConstructorSpec)
                .addFunctions(constructorSpecs)
                .addType(messageBaseClassSpec)
                .addProperty(actorPropertySpec)
                .addFunctions(delegateMethodSpecs)
                .build()
    }

    private fun actorPropertySpec(visibleMethodElements: List<ExecutableElement>, messageBaseClassName: ClassName): PropertySpec {
        val actorInitializerFragments = mutableListOf(
                """ |actor(context) {
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
        return PropertySpec
                .builder(ACTOR_PROP_NAME, actorClassName, KModifier.PRIVATE)
                .initializer(actorInitializer)
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
            |          val result = delegate.$delegateMethodName($parameterList)
            |          msg.response?.complete(result)
            |        } catch (t: Throwable) {
            |           val currentThread = Thread.currentThread()
            |           msg.response?.completeExceptionally(t)
            |               ?: currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, t)
            |        }
            |      }
            |""".trimMargin()
    }

    private fun delegteMethodSpecs(visibleMethodElements: List<ExecutableElement>, messageBaseClassName: ClassName) = visibleMethodElements.flatMap { method ->
        arrayListOf(
                delegateMethodSpec(method, messageBaseClassName),
                fireAndForgetDelegateMethodSpec(method, messageBaseClassName),
                asyncDelegateMethodSpec(method, messageBaseClassName)
        )
    }

    private fun delegateMethodSpec(method: ExecutableElement, messageBaseClassName: ClassName): FunSpec {
        val delegateReturnTypeName = method.returnType.asTypeName()
        val returnTypeName = ParameterizedTypeName.get(
                ClassName("kotlinx.coroutines.experimental", "CompletableDeferred"),
                delegateReturnTypeName)

        val delegateMethodName = method.simpleName.toString()
        val methodName = delegateMethodName
        val messageName = messageBaseClassName.simpleName() + "." + delegateMethodName.capitalize()

        val responseParameterSpec = ParameterSpec.builder(RESPONSE_PARAMETER_NAME, returnTypeName)
                .defaultValue("$returnTypeName()")
                .build()
        val parameterSpecs = method.parameters
                .map { parameter ->
                    ParameterSpec.builder(parameter.simpleName.toString(), parameter.asType().asTypeName())
                            .build()
                }
        val messageParameters = listOf("response") +
                method.parameters.map { parameter -> parameter.simpleName }
        val messageParameterList = messageParameters.joinToString()

        return FunSpec.builder(methodName)
                .addModifiers(KModifier.SUSPEND)
                .addParameters(parameterSpecs)
                .addParameter(responseParameterSpec)
                .returns(returnTypeName)
                .addCode(
                        """ |actor.send($messageName($messageParameterList))
                            |return $RESPONSE_PARAMETER_NAME
                            |""".trimMargin())
                .build()
    }

    private fun fireAndForgetDelegateMethodSpec(method: ExecutableElement, messageBaseClassName: ClassName): FunSpec {
        val delegateMethodName = method.simpleName.toString()

        val parameterSpecs = method.parameters
                .map { parameter ->
                    ParameterSpec.builder(parameter.simpleName.toString(), parameter.asType().asTypeName())
                            .build()
                }
        val messageParameters = listOf("null") + method.parameters
                .map { parameter -> parameter.simpleName }
        val messageParameterList = messageParameters.joinToString()

        val messageName = messageBaseClassName.simpleName() + "." + delegateMethodName.capitalize()
        return FunSpec.builder(method.simpleName.toString() + FIREANDFORGET_METHOD_SUFFIX)
                .addModifiers(KModifier.SUSPEND)
                .addParameters(parameterSpecs)
                .returns(UNIT)
                .addCode("actor.send($messageName($messageParameterList))\n")
                .build()

    }

    private fun asyncDelegateMethodSpec(method: ExecutableElement, messageBaseClassName: ClassName): FunSpec {
        val delegateMethodName = method.simpleName.toString()
        val delegateReturnType = method.returnType.asTypeName()

        val parameterSpecs = method.parameters
                .map { parameter ->
                    ParameterSpec.builder(parameter.simpleName.toString(), parameter.asType().asTypeName())
                            .build()
                }
        val messageParameters = method.parameters
                .map { parameter -> parameter.simpleName }
        val messageParameterList = messageParameters.joinToString()

        return FunSpec.builder(method.simpleName.toString() + ASYNC_METHOD_SUFFIX)
                .addModifiers(KModifier.SUSPEND)
                .addParameters(parameterSpecs)
                .returns(delegateReturnType)
                .addCode("return $delegateMethodName($messageParameterList).await()\n")
                .build()

    }

    private fun messageTypeSpecs(visibleMethodElements: List<ExecutableElement>, baseClassName: ClassName) = visibleMethodElements.map { method ->
        val methodName = method.simpleName.toString()
        val methodParameters = method.parameters
        val responseTypeName = ParameterizedTypeName.get(
                ClassName("kotlinx.coroutines.experimental", "CompletableDeferred"),
                method.returnType.asTypeName()).asNullable()
        val constructorResponseParameterSpec = ParameterSpec
                .builder(RESPONES_PROP_NAME, responseTypeName)
                .build()
        val constructorParametersFromMethodSpec = methodParameters.toParameterSpecList()
        val constructorSpec = FunSpec.constructorBuilder()
                .addParameter(constructorResponseParameterSpec)
                .addParameters(constructorParametersFromMethodSpec)
                .build()
        val responsePropertySpec = PropertySpec
                .builder(RESPONES_PROP_NAME, responseTypeName)
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

