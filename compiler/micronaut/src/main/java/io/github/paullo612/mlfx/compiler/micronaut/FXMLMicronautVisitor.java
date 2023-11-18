/*
 * Copyright 2023 Paullo612
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.paullo612.mlfx.compiler.micronaut;

import com.google.auto.service.AutoService;
import io.github.paullo612.mlfx.compiler.CompileFXMLVisitor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@AutoService(TypeElementVisitor.class)
public class FXMLMicronautVisitor implements TypeElementVisitor<Object, Object> {

    private static final boolean IS_MICRONAUT_4_OR_HIGHER = VersionUtils.isAtLeastMicronautVersion("4.0.0");

    private static final String FXML_ANNOTATION = "javafx.fxml.FXML";

    private static Introspected.Visibility ANY_VISIBILITY;

    private AnnotationValueBuilder<Introspected> setVisibility(AnnotationValueBuilder<Introspected> builder) {
        if (!IS_MICRONAUT_4_OR_HIGHER) {
            return builder;
        }

        if (ANY_VISIBILITY == null) {
            ANY_VISIBILITY = Arrays.stream(Introspected.Visibility.values())
                    .filter(v -> "ANY".equals(v.name()))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
        }

        return builder
                .member("visibility", ANY_VISIBILITY);
    }

    private void markAsIntrospected(ClassElement classElement, VisitorContext context) {
        if (classElement.hasStereotype(Introspected.class)) {
            // Already @Introspected. We've already marked this class, or user knows better.
            return;
        }

        List<FieldElement> fields = classElement.getEnclosedElements(
                ElementQuery.ALL_FIELDS
                        .onlyDeclared()
                        .annotated(m -> m.hasDeclaredAnnotation(FXML_ANNOTATION))
        );

        for (FieldElement field : fields) {
            if (field.isPublic()) {
                context.info(
                        "Controller field is public and is annotated by @FXML annotation. Consider removing it as it is"
                                + " not needed.",
                        field
                );
            }

            if (field.isPrivate()) {
                context.fail(
                        "Controller field is private and is annotated by @FXML annotation. Private fields cannot be set"
                                + " to controller by Micronaut.",
                        field
                );
            }

            if (!IS_MICRONAUT_4_OR_HIGHER) {
                if (field.isProtected()) {
                    context.fail(
                            "Controller field is protected and is annotated by @FXML annotation. Protected fields"
                                    + " cannot be set to controller by Micronaut version lower than 4.0.0.",
                            field
                    );
                }
            }
        }

        String[] toInclude = fields.stream()
                .filter(f -> !f.isPublic())
                .map(Element::getName)
                .toArray(String[]::new);

        String[] toExclude;

        if (toInclude.length == 0) {
            // No fields to include. Exclude everything.
            toExclude = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared()).stream()
                    .map(Element::getName)
                    .toArray(String[]::new);
        } else {
            toExclude = new String[0];
        }

        classElement.annotate(
                setVisibility(AnnotationValue.builder(Introspected.class)
                        .member("accessKind", Introspected.AccessKind.FIELD)
                        .member("includes", toInclude)
                        .member("excludes", toExclude)
                )
                        .build()
        );
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (!element.hasAnnotation(FXML_ANNOTATION)) {
            return;
        }

        ClassElement classElement = element.getOwningType();

        markAsIntrospected(classElement, context);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasAnnotation(FXML_ANNOTATION)) {
            return;
        }

        ClassElement classElement = element.getOwningType();
        markAsIntrospected(classElement, context);

        if (element.isPublic()) {
            context.info(
                    "Controller method is public and is annotated by @FXML annotation. Consider removing it as it is"
                            + " not needed.",
                    element
            );
        }

        if (element.isPrivate()) {
            context.fail(
                    "Controller method is private and is annotated by @FXML annotation. Private controller methods"
                            + " cannot be called by Micronaut.",
                    element
            );
            return;
        }

        element.annotate(Executable.class);
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(FXML_ANNOTATION);
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public int getOrder() {
        return CompileFXMLVisitor.ORDER - 1;
    }
}
