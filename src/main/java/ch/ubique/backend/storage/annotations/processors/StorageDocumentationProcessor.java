package ch.ubique.backend.storage.annotations.processors;

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
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.lang.model.element.Modifier.*;

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
            final var outputMappings = roundEnv.getElementsAnnotatedWith(OutputMapping.class)
                    .stream()
                    .filter(outputMapping -> outputMapping.getEnclosingElement().equals(element))
                    .collect(Collectors.toUnmodifiableSet());

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
}
