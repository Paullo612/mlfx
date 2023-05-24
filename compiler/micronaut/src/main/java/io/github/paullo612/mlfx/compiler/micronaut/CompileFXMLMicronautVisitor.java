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
import io.github.paullo612.mlfx.api.CompiledFXMLLoader;
import io.github.paullo612.mlfx.api.GeneratedByMLFX;
import io.github.paullo612.mlfx.compiler.CompileFXMLVisitor;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@AutoService(TypeElementVisitor.class)
public class CompileFXMLMicronautVisitor implements TypeElementVisitor<GeneratedByMLFX, Object> {

    private final Set<ClassElement> services = new TreeSet<>(Comparator.comparing(Element::getName));

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasDeclaredAnnotation(GeneratedByMLFX.class)) {
            // Not interested in nested expressions.
            return;
        }

        services.add(element);
    }

    private boolean writeServiceDescriptorsUsingAPI(VisitorContext context) {
        Method method;

        try {
            method = context.getClass().getMethod("visitServiceDescriptor", Class.class, String.class, Element.class);
        } catch (NoSuchMethodException e) {
            return false;
        }

        boolean firstInvokeFailed = true;

        for (ClassElement element : services) {
            try {
                method.invoke(context, CompiledFXMLLoader.class, element.getName(), element);
            } catch (IllegalAccessException e) {
                if (firstInvokeFailed) {
                    return false;
                }
            } catch (InvocationTargetException e) {
                context.fail(
                        "Failed to write to ServiceLoader descriptor for compiled FXML loaders: "
                                + e.getTargetException().getMessage(),
                        element
                );
                return true;
            }

            firstInvokeFailed = false;
        }

        return true;
    }

    private void writeServiceDescriptorByHand(VisitorContext context) {
        ClassElement[] elements = services.toArray(new ClassElement[0]);

        Optional<GeneratedFile> serviceFile =
                context.visitMetaInfFile("services/" + CompiledFXMLLoader.class.getName(), elements);

        if (serviceFile.isEmpty()) {
            context.fail("Failed to create ServiceLoader descriptor for compiled FXML loaders.", elements[0]);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(serviceFile.get().openWriter())) {
            Iterator<ClassElement> it = services.iterator();

            while (it.hasNext()) {
                ClassElement element = it.next();
                writer.write(element.getName());

                if (it.hasNext()) {
                    writer.newLine();
                }
            }
        } catch (IOException x) {
            context.fail("Failed to open ServiceLoader descriptor for compiled FXML loaders.", elements[0]);
        }
    }

    @Override
    public void finish(VisitorContext context) {
        if (services.isEmpty()) {
            return;
        }

        try {
            // Do some hackery there. Micronaut prior to 3.5.0 is not capable of writing service descriptors correctly.
            //  There were |ServiceDescriptionProcessor| that relied solely on visiting generated classes,
            //  |element.annotate(...)| had no effect. But in 3.5.0 |visitServiceDescriptor(...)| with originated
            //  element were introduced, that does its work well. So, we'll be conditional on Micronaut version here.
            if (VersionUtils.isAtLeastMicronautVersion("3.5.0") && writeServiceDescriptorsUsingAPI(context)) {
                return;
            }

            writeServiceDescriptorByHand(context);
        } finally {
            services.clear();
        }
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(GeneratedByMLFX.class.getName());
    }

    @Override
    public int getOrder() {
        return CompileFXMLVisitor.ORDER - 1;
    }
}
