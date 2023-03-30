package ch.ubique.backend.storage.annotations.processors;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.DeleteMapping;
import ch.ubique.backend.storage.annotations.InputMapping;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.Storage;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.FileOutput;
import ch.ubique.backend.storage.output.JsonOutput;
import ch.ubique.backend.storage.output.Output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

@AutoService(Processor.class)
public class StorageImplementationProcessor extends AbstractStorageAnnotationProcessor {

    private static String getPathTemplate(final String basePath, final String path) {
        return basePath.isBlank() ? path : basePath + "/" + path;
    }

    private static boolean isByteArray(final TypeMirror typeMirror) {
        return typeMirror.getKind() == TypeKind.ARRAY
                && ((ArrayType) typeMirror).getComponentType().getKind() == TypeKind.BYTE;
    }

    @Override
    protected Set<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Storage.class, OutputMapping.class, InputMapping.class, DeleteMapping.class);
    }

    @Override
    public boolean process(
            final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

        // Get a map for convenience, that maps for each annotation class from parent to annotated
        // children
        // We use it to get e.g. all @OutputMapping annotated elements for a given element annotated
        // with @Storage
        final var annotatedElements =
                supportedAnnotations().stream()
                        .collect(
                                Collectors.toMap(
                                        cls -> cls,
                                        cls ->
                                                roundEnv.getElementsAnnotatedWith(cls).stream()
                                                        .collect(
                                                                Collectors.groupingBy(
                                                                        Element
                                                                                ::getEnclosingElement))));

        // Iterate over all elements annotated with @Storage
        for (final var element : roundEnv.getElementsAnnotatedWith(Storage.class)) {

            // Start generating an implementation for the given element annotated with @Storage
            final var injectObjectMapper = shouldInjectObjectMapper(element);
            final var storageBasePath = getStorageBasePath(element);
            final var classBuilder = generateClass(element, injectObjectMapper);

            // Generate methods for each element annotated with @InputMapping
            Optional.ofNullable(annotatedElements.get(InputMapping.class).get(element))
                    .ifPresent(
                            inputMappingElements ->
                                    classBuilder.addMethods(
                                            inputMappingElements.stream()
                                                    .map(
                                                            inputMapping ->
                                                                    createInputMappingMethod(
                                                                            (ExecutableElement)
                                                                                    inputMapping,
                                                                            storageBasePath))
                                                    .toList()));

            // Generate methods for each element annotated with @OutputMapping
            Optional.ofNullable(annotatedElements.get(OutputMapping.class).get(element))
                    .ifPresent(
                            outputMappingElements ->
                                    classBuilder.addMethods(
                                            outputMappingElements.stream()
                                                    .map(
                                                            outputMapping ->
                                                                    createOutputMappingMethod(
                                                                            outputMapping,
                                                                            storageBasePath))
                                                    .toList()));

            // Generate methods for each element annotated with @DeleteMapping
            Optional.ofNullable(annotatedElements.get(DeleteMapping.class).get(element))
                    .ifPresent(
                            deleteMappingElements ->
                                    classBuilder.addMethods(
                                            deleteMappingElements.stream()
                                                    .map(
                                                            deleteMapping ->
                                                                    createDeleteMappingMethod(
                                                                            deleteMapping,
                                                                            storageBasePath))
                                                    .toList()));

            // Write class to filer, it will be available as generated source
            writeClass(classBuilder, element);
        }

        // Return false: marked as unclaimed -> i.e. other processors are also called
        return false;
    }

    private boolean shouldInjectObjectMapper(final Element interfaceElement) {
        return interfaceElement.getAnnotation(Storage.class).injectObjectMapper();
    }

    private String getStorageBasePath(final Element interfaceElement) {
        return interfaceElement.getAnnotation(Storage.class).basePath();
    }

    private TypeSpec.Builder generateClass(
            final Element element, final boolean injectObjectMapper) {
        final var className = element.getSimpleName() + "Implementation";

        return TypeSpec.classBuilder(className)
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(Service.class)
                .addSuperinterface(element.asType())
                .addField(StorageClient.class, "storageClient", PRIVATE, FINAL)
                .addField(ObjectMapper.class, "objectMapper", PRIVATE, FINAL)
                .addMethod(createConstructor(element, injectObjectMapper));
    }

    private MethodSpec createConstructor(
            final Element interfaceElement, final boolean injectObjectMapper) {

        final var clientParameterBuilder =
                ParameterSpec.builder(StorageClient.class, "storageClient").addModifiers(FINAL);

        final var storageClientQualifier =
                interfaceElement.getAnnotation(Storage.class).clientQualifier();

        if (StringUtils.isNotBlank(storageClientQualifier)) {
            clientParameterBuilder.addAnnotation(
                    AnnotationSpec.builder(Qualifier.class)
                            .addMember("value", "$S", storageClientQualifier)
                            .build());
        }

        final var constructorBuilder =
                MethodSpec.constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(clientParameterBuilder.build())
                        .addStatement("this.storageClient = storageClient");

        if (injectObjectMapper) {
            final var objectMapperParameterBuilder =
                    ParameterSpec.builder(ObjectMapper.class, "objectMapper").addModifiers(FINAL);

            final var objectMapperQualifier =
                    interfaceElement.getAnnotation(Storage.class).objectMapperQualifier();

            if (StringUtils.isNotBlank(objectMapperQualifier)) {
                objectMapperParameterBuilder.addAnnotation(
                        AnnotationSpec.builder(Qualifier.class)
                                .addMember("value", "$S", objectMapperQualifier)
                                .build());
            }

            constructorBuilder
                    .addParameter(objectMapperParameterBuilder.build())
                    .addStatement("this.objectMapper = objectMapper");
        } else {
            constructorBuilder.addStatement("this.objectMapper = new $T()", ObjectMapper.class);
        }

        return constructorBuilder.build();
    }

    private MethodSpec createInputMappingMethod(
            final ExecutableElement method, final String storageBasePath) {

        final var parameters = getParameters(method);
        final var pathTemplate =
                getPathTemplate(storageBasePath, method.getAnnotation(InputMapping.class).path());
        final var path = getPath(parameters, pathTemplate);

        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .addException(IOException.class)
                .addParameters(parameters.stream().map(this::generateParameter).toList())
                .returns(TypeName.get(method.getReturnType()))
                .addStatement("return $L", createInputMappingReturnValue(method, path))
                .build();
    }

    private CodeBlock createInputMappingReturnValue(
            final ExecutableElement method, final CodeBlock path) {

        final var returnType = method.getReturnType();
        final var inputMapping = method.getAnnotation(InputMapping.class);

        final var inputStream = CodeBlock.of("storageClient.read($L)", path);
        final var bytes =
                CodeBlock.of("$T.readBytesAndClose($L)", InputStreamUtils.class, inputStream);

        if (typeUtils.isSameType(returnType, typeMirrorOf(InputStream.class))) {
            return inputStream;
        }
        if (isByteArray(returnType)) {
            return bytes;
        }
        if (typeUtils.isSameType(returnType, typeMirrorOf(ByteFile.class))) {
            final var contentType = StringUtils.defaultIfBlank(inputMapping.contentType(), null);
            final var fileName =
                    CodeBlock.of("$T.of($L).getFileName().toString()", Path.class, path);
            return CodeBlock.of(
                    "$T.fromBytes(\n$>$L, \n$L, \n$S)$<",
                    ByteFile.class,
                    bytes,
                    fileName,
                    contentType);
        }

        final var objectMapper = getObjectMapper(getParameters(method));
        return CodeBlock.of("$L.readValue($L, $T.class)", objectMapper, inputStream, returnType);
    }

    private MethodSpec createOutputMappingMethod(
            final Element method, final String storageBasePath) {
        final var annotation = method.getAnnotation(OutputMapping.class);
        final var parameters = getParameters(method);
        final var pathTemplate = getPathTemplate(storageBasePath, annotation.path());
        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .addParameters(parameters.stream().map(this::generateParameter).toList())
                .addStatement(
                        "storageClient.write(\n$>$L,\n$L,\n$L)$<",
                        getOutput(parameters, annotation),
                        getPath(parameters, pathTemplate),
                        getMetadata(parameters))
                .returns(void.class)
                .addException(IOException.class)
                .build();
    }

    private MethodSpec createDeleteMappingMethod(
            final Element method, final String storageBasePath) {
        final var annotation = method.getAnnotation(DeleteMapping.class);
        final var parameters = getParameters(method);
        final var pathTemplate = getPathTemplate(storageBasePath, annotation.path());
        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .addParameters(parameters.stream().map(this::generateParameter).toList())
                .addStatement("return storageClient.delete($L)", getPath(parameters, pathTemplate))
                .returns(boolean.class)
                .addException(IOException.class)
                .build();
    }

    private Object getMetadata(final List<? extends VariableElement> parameters) {
        return parameters.stream()
                .filter(p -> TypeName.get(p.asType()).equals(TypeName.get(OutputMetaData.class)))
                .findFirst()
                .map(Object::toString)
                .orElse("null");
    }

    private CodeBlock getOutput(
            final List<? extends VariableElement> parameters, final OutputMapping outputMapping) {
        final var inner = getInnerOutput(parameters, outputMapping);
        if (outputMapping.gZip()) {
            return CodeBlock.of("$L$Z.gZip()", inner);
        }
        return inner;
    }

    private CodeBlock getInnerOutput(
            final List<? extends VariableElement> parameters, final OutputMapping outputMapping) {
        final var storageObjectParameter = getStorageObjectParameter(parameters);

        final var outputType =
                elementUtils.getTypeElement(Output.class.getCanonicalName()).asType();
        final var outputParameter =
                parameters.stream()
                        .filter(param -> typeUtils.isAssignable(param.asType(), outputType))
                        .findFirst();
        if (outputParameter.isPresent()) {
            return CodeBlock.of("$L", outputParameter.get().getSimpleName().toString());
        }

        // Create output object from first parameter
        final var paramName = storageObjectParameter.getSimpleName().toString();
        final var paramType = storageObjectParameter.asType();
        final var contentType = outputMapping.contentType();

        if (typeUtils.isAssignable(paramType, typeMirrorOf(File.class))) {
            if (StringUtils.isEmpty(contentType)) {
                return CodeBlock.of("new $T($L)", FileOutput.class, paramName);
            }
            return CodeBlock.of("new $T($L, $S)", FileOutput.class, paramName, contentType);
        }

        if (isByteArray(paramType)) {
            if (StringUtils.isEmpty(contentType)) {
                return CodeBlock.of("new $T($L)", ByteOutput.class, paramName);
            }
            return CodeBlock.of("new $T($L, $S)", ByteOutput.class, paramName, contentType);
        }

        return CodeBlock.of(
                "new $T<>($L, $L)", JsonOutput.class, paramName, getObjectMapper(parameters));
    }

    private CodeBlock getObjectMapper(final List<? extends VariableElement> parameters) {
        final var objectMapperType =
                elementUtils.getTypeElement(ObjectMapper.class.getCanonicalName()).asType();

        final var objectMapperParameter =
                parameters.stream()
                        .filter(param -> typeUtils.isAssignable(param.asType(), objectMapperType))
                        .findFirst();

        // Object mapper was passed as method parameter, use it.
        // noinspection OptionalIsPresent
        if (objectMapperParameter.isPresent()) {
            return CodeBlock.of("$L", objectMapperParameter.get().getSimpleName());
        }

        return CodeBlock.of("this.objectMapper");
    }

    private CodeBlock getPath(
            final List<? extends VariableElement> parameters, final String pathTemplate) {
        final var replaceCalls =
                CodeBlock.join(
                        parameters.stream()
                                .filter(p -> p.getAnnotation(PathVariable.class) != null)
                                .map(this::stringReplace)
                                .toList(),
                        "\n.");

        if (replaceCalls.isEmpty()) {
            return CodeBlock.of("$S", pathTemplate);
        }
        return CodeBlock.of("$S\n$>.$L$<", pathTemplate, replaceCalls);
    }

    private CodeBlock stringReplace(final VariableElement parameter) {
        final var parameterName = parameter.toString();
        final var isPrimitive = TypeName.get(parameter.asType()).isPrimitive();
        final var toString =
                CodeBlock.of(isPrimitive ? "String.valueOf($L)" : "$L.toString()", parameterName);
        return CodeBlock.of("replace(\"{$L}\", $L)", parameterName, toString);
    }

    private ParameterSpec generateParameter(final VariableElement parameter) {
        return ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.toString(), FINAL)
                .build();
    }

    private TypeMirror typeMirrorOf(final Class<?> cls) {
        return elementUtils.getTypeElement(cls.getCanonicalName()).asType();
    }
}
