package ch.ubique.backend.storage.annotations.processors;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.Storage;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import io.swagger.v3.oas.annotations.Operation;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;

//@AutoService(Processor.class)
public class StorageDocumentationProcessor extends AbstractStorageAnnotationProcessor {

    @Override
    protected Set<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Storage.class, OutputMapping.class);
    }

    @Override
    public boolean process(
            final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

        // Iterate over all elements annotated with @Storage with generateDocumentation == true
        roundEnv.getElementsAnnotatedWith(Storage.class).stream()
                .filter(element -> element.getAnnotation(Storage.class).generateDocumentation())
                .forEach(
                        element -> {
                            final var storageAnnotation = element.getAnnotation(Storage.class);

                            // Start generating an implementation for the given element
                            // annotated with @Storage
                            final var classBuilder = generateClass(storageAnnotation, element);

                            // Get all the output mappings of the given element
                            classBuilder.addMethods(
                                    roundEnv.getElementsAnnotatedWith(OutputMapping.class).stream()
                                            .filter(
                                                    outputMapping -> {
                                                        final var annotation =
                                                                outputMapping.getAnnotation(
                                                                        OutputMapping.class);
                                                        return annotation.generateDocumentation()
                                                                || annotation
                                                                        .codeGenerationType()
                                                                        .generateDocumentationCode();
                                                    })
                                            .filter(
                                                    outputMapping ->
                                                            outputMapping
                                                                    .getEnclosingElement()
                                                                    .equals(element))
                                            .map(this::generateMethodForOutputMapping)
                                            .toList());

                            // Write class to filer, it will be available as generated
                            // source
                            final var storageDocumentationPackageName =
                                    StringUtils.isNotBlank(storageAnnotation.documentationPackage())
                                            ? storageAnnotation.documentationPackage()
                                            : element.getEnclosingElement().toString()
                                                    + ".documentation";
                            writeClass(classBuilder, storageDocumentationPackageName);
                        });

        // Return false: marked as unclaimed -> i.e. other processors are also called
        return false;
    }

    private TypeSpec.Builder generateClass(final Storage storageAnnotation, final Element element) {
        return TypeSpec.classBuilder(element.getSimpleName() + "Documentation")
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(RestController.class)
                .addAnnotation(
                        AnnotationSpec.builder(RequestMapping.class)
                                .addMember(
                                        "value",
                                        "$S",
                                        Stream.of(
                                                        storageAnnotation.baseUrl(),
                                                        storageAnnotation.basePath())
                                                .filter(StringUtils::isNotBlank)
                                                .collect(Collectors.joining("/")))
                                .build())
        // the following does not work, as Swagger-UI does not show the controller
        /*.addAnnotation(
        AnnotationSpec.builder(ConditionalOnExpression.class)
                .addMember("value", "$S", "false")
                .build())*/ ;
    }

    private MethodSpec generateMethodForOutputMapping(final Element method) {
        final var outputMappingAnnotation = method.getAnnotation(OutputMapping.class);
        final var outputMappingMethodParameters = getParameters(method);

        final var getMappingAnnotationBuilder =
                AnnotationSpec.builder(GetMapping.class)
                        .addMember("value", "$S", outputMappingAnnotation.path());
        if (StringUtils.isNotBlank(outputMappingAnnotation.contentType())) {
            getMappingAnnotationBuilder.addMember(
                    "produces", "$S", outputMappingAnnotation.contentType());
        }

        final var getMappingMethodParameters =
                outputMappingMethodParameters.stream()
                        .skip(1)
                        .filter(element -> element.getAnnotation(PathVariable.class) != null)
                        .map(this::generateParameter)
                        .toList();

        final var documentedClass = getDocumentedClass(outputMappingAnnotation);
        final var hasDocumentClass = !TypeName.OBJECT.equals(documentedClass);

        final var typeOfStorageObject =
                TypeName.get(getStorageObjectParameter(outputMappingMethodParameters).asType());

        final var returnType = hasDocumentClass ? documentedClass : typeOfStorageObject;

        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(getMappingAnnotationBuilder.build())
                .addAnnotation(
                        AnnotationSpec.builder(Operation.class)
                                .addMember(
                                        "description",
                                        "$S",
                                        "Documentation for blob storage endpoint, accessible"
                                                + " under {BASE_URL}")
                                .build())
                .returns(returnType)
                .addParameters(getMappingMethodParameters)
                .addStatement(
                        "throw new IllegalCallerException(\"this controller method exists only to"
                                + " document blob storage endpoints and must not be called\")")
                .build();
    }

    private TypeName getDocumentedClass(final OutputMapping annotation) {
        try {
            return ClassName.get(annotation.documentedClass()); // this should throw
        } catch (final MirroredTypeException mte) {
            return TypeName.get(mte.getTypeMirror());
        }
    }

    private ParameterSpec generateParameter(final VariableElement parameter) {
        return ParameterSpec.builder(TypeName.get(parameter.asType()), parameter.toString(), FINAL)
                .addAnnotation(AnnotationSpec.get(parameter.getAnnotation(PathVariable.class)))
                .build();
    }
}
