package ch.ubique.backend.storage.annotations.processors;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

import ch.ubique.backend.storage.annotations.OutputMapping;
import ch.ubique.backend.storage.annotations.Storage;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public class StorageDocumentationProcessor extends AbstractStorageAnnotationProcessor {

    @Override
    protected Set<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Storage.class, OutputMapping.class);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        // Iterate over all elements annotated with @Storage
        for (final var element : roundEnv.getElementsAnnotatedWith(Storage.class)) {

            // Start generating an implementation for the given element annotated with @Storage
            final var classBuilder = generateClass(element);

            // Get all the output mappings of the given element
            classBuilder.addMethods(
                    roundEnv.getElementsAnnotatedWith(OutputMapping.class).stream()
                            .filter(
                                    outputMapping ->
                                            outputMapping.getEnclosingElement().equals(element))
                            .map(this::generateMethodForOutputMapping)
                            .toList());

            // Write class to filer, it will be available as generated source
            writeClass(classBuilder, element);
        }

        // Return false: marked as unclaimed -> i.e. other processors are also called
        return false;
    }

    private TypeSpec.Builder generateClass(final Element element) {
        final var className = element.getSimpleName() + "Documentation";
        return TypeSpec.classBuilder(className)
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(RestController.class);
    }

    private MethodSpec generateMethodForOutputMapping(final Element method) {
        return MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(PUBLIC)
                .addAnnotation(GetMapping.class)
                .build();
    }
}
