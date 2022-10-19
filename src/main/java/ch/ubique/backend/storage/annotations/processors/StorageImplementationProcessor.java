package ch.ubique.backend.storage.annotations.processors;

import static javax.lang.model.element.Modifier.*;

import ch.ubique.backend.storage.Output;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.annotations.InputMapping;
import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.Storage;
import ch.ubique.backend.storage.output.ByteOutput;
import ch.ubique.backend.storage.output.FileOutput;
import ch.ubique.backend.storage.output.JsonOutput;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
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

@AutoService(Processor.class)
public class StorageImplementationProcessor extends AbstractStorageAnnotationProcessor {

    @Override
    protected Set<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Storage.class, OutputMapping.class, InputMapping.class);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

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
            final var classBuilder = generateClass(element);

            // Generate methods for each element annotated with @InputMapping
            Optional.ofNullable(annotatedElements.get(InputMapping.class).get(element))
                    .ifPresent(
                            inputMappingElements ->
                                    classBuilder.addMethods(
                                            inputMappingElements.stream()
                                                    .map(this::createInputMappingMethod)
                                                    .toList()));

            // Generate methods for each element annotated with @OutputMapping
            Optional.ofNullable(annotatedElements.get(OutputMapping.class).get(element))
                    .ifPresent(
                            outputMappingElements ->
                                    classBuilder.addMethods(
                                            outputMappingElements.stream()
                                                    .map(this::createOutputMappingMethod)
                                                    .toList()));

            // Write class to filer, it will be available as generated source
            writeClass(classBuilder, element);
        }

        // Return false: marked as unclaimed -> i.e. other processors are also called
        return false;
    }

    private TypeSpec.Builder generateClass(final Element element) {
        final var className = element.getSimpleName() + "Implementation";
        return TypeSpec.classBuilder(className)
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(Service.class)
                .addSuperinterface(element.asType())
                .addField(FieldSpec.builder(StorageClient.class, "client", PRIVATE, FINAL).build())
                .addMethod(createConstructor(element));
    }

    private MethodSpec createConstructor(final Element interfaceElement) {
        return MethodSpec.constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(
                        ParameterSpec.builder(StorageClient.class, "client")
                                .addAnnotation(
                                        AnnotationSpec.builder(Qualifier.class)
                                                .addMember(
                                                        "value",
                                                        "$S",
                                                        interfaceElement
                                                                .getAnnotation(Storage.class)
                                                                .clientQualifier())
                                                .build())
                                .build())
                .addStatement("this.client = client")
                .build();
    }

    private MethodSpec createInputMappingMethod(final Element method) {
        final var parameters = getParameters(method);
        final var pathTemplate = method.getAnnotation(InputMapping.class).path();
        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .returns(InputStream.class)
                .addException(IOException.class)
                .addParameters(parameters.stream().map(this::generateParameter).toList())
                .addStatement("return client.read($L)", getPath(method, pathTemplate))
                .build();
    }

    private MethodSpec createOutputMappingMethod(final Element method) {
        final var annotation = method.getAnnotation(OutputMapping.class);
        final var parameters = getParameters(method);
        final var pathTemplate = annotation.path();
        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .addParameters(parameters.stream().map(this::generateParameter).toList())
                .addStatement(
                        "client.write($L, $L, $L)",
                        getOutput(parameters, annotation),
                        getPath(method, pathTemplate),
                        getMetadata(parameters))
                .returns(void.class)
                .addException(IOException.class)
                .build();
    }

    private Object getMetadata(List<? extends VariableElement> parameters) {
        return parameters.stream()
                .filter(p -> TypeName.get(p.asType()).equals(TypeName.get(OutputMetaData.class)))
                .findFirst()
                .map(Object::toString)
                .orElse("null");
    }

    private CodeBlock getOutput(
            List<? extends VariableElement> parameters, OutputMapping outputMapping) {
        final var inner = getInnerOutput(parameters, outputMapping);
        if (outputMapping.gZip()) {
            return CodeBlock.of("$L.gZip()", inner);
        }
        return inner;
    }

    private CodeBlock getInnerOutput(
            List<? extends VariableElement> parameters, OutputMapping outputMapping) {
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException(
                    "Methods annotated with "
                            + OutputMapping.class
                            + " need to have at least one parameter for the output");
        }

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
        final var firstParameter = parameters.get(0);
        final var paramType = firstParameter.asType();
        final var contentType = outputMapping.contentType();

        if (typeUtils.isAssignable(
                paramType, elementUtils.getTypeElement(File.class.getCanonicalName()).asType())) {
            if (StringUtils.isEmpty(contentType)) {
                return CodeBlock.of(
                        "new $T($L)", FileOutput.class, firstParameter.getSimpleName().toString());
            }
            return CodeBlock.of(
                    "new $T($L, $S)",
                    FileOutput.class,
                    firstParameter.getSimpleName().toString(),
                    contentType);
        }

        if (paramType.getKind() == TypeKind.ARRAY
                && ((ArrayType) paramType).getComponentType().getKind() == TypeKind.BYTE) {
            if (StringUtils.isEmpty(contentType)) {
                return CodeBlock.of(
                        "new $T($L)", ByteOutput.class, firstParameter.getSimpleName().toString());
            }
            return CodeBlock.of(
                    "new $T($L, $S)",
                    ByteOutput.class,
                    firstParameter.getSimpleName().toString(),
                    contentType);
        }

        return CodeBlock.of(
                "new $T<>($L)", JsonOutput.class, firstParameter.getSimpleName().toString());
    }

    private CodeBlock getPath(final Element method, String pathTemplate) {
        final var replaceCalls =
                getParameters(method).stream()
                        .filter(p -> p.getAnnotation(PathVariable.class) != null)
                        .map(this::stringReplace)
                        .collect(Collectors.joining("."));

        if (replaceCalls.isEmpty()) {
            return CodeBlock.of("$S", pathTemplate);
        }
        return CodeBlock.of("$S.$L", pathTemplate, replaceCalls);
    }

    private String stringReplace(VariableElement parameter) {
        final var parameterName = parameter.toString();
        final var isPrimitive = TypeName.get(parameter.asType()).isPrimitive();
        final var toStringFormat = isPrimitive ? "String.valueOf(%s)" : "%s.toString()";
        return String.format(
                "replace(\"{%s}\", %s)",
                parameterName, String.format(toStringFormat, parameterName));
    }

    private List<? extends VariableElement> getParameters(Element method) {
        return ((ExecutableElement) method).getParameters();
    }

    private ParameterSpec generateParameter(final VariableElement parameter) {
        return ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.toString(), FINAL)
                .build();
    }
}
