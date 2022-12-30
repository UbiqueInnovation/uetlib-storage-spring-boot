package ch.ubique.backend.storage.annotations.processors;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public abstract class AbstractStorageAnnotationProcessor extends AbstractProcessor {

    protected Filer filer;
    protected Types typeUtils;
    protected Elements elementUtils;

    protected abstract Set<Class<? extends Annotation>> supportedAnnotations();

    @Override
    public final synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public final Set<String> getSupportedAnnotationTypes() {
        return supportedAnnotations().stream()
                .map(Class::getCanonicalName)
                .collect(Collectors.toUnmodifiableSet());
    }

    protected final void writeClass(final TypeSpec.Builder classBuilder, final Element element) {
        writeClass(classBuilder, element.getEnclosingElement().toString());
    }

    protected final void writeClass(final TypeSpec.Builder classBuilder, final String packageName) {
        try {
            JavaFile.builder(packageName, classBuilder.build()).build().writeTo(filer);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("java:S1452")
    protected final List<? extends VariableElement> getParameters(final Element method) {
        return ((ExecutableElement) method).getParameters();
    }
}
