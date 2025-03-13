/*
 * Copyright 2025 Paullo612
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
package io.github.paullo612.mlfx.compiler.elements;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.PropertyElement;

public final class ElementUtils {

    private static final String FXML_ANNOTATION_NAME = "javafx.fxml.FXML";

    private static ClassElement smashUpGeneric(ClassElement classElement) {
        //NB: FXML is designed to work this way...
        if (classElement.isGenericPlaceholder()) {
            classElement = ((GenericPlaceholderElement) classElement).getBounds().get(0);
        }

        return classElement;
    }

    public static boolean isAssignable(ClassElement from, ClassElement to) {
        // NB: primitives are considered assignable to boxed types since Micronaut 4.0.0
        if (from.isPrimitive() && !to.isPrimitive() || !from.isPrimitive() && to.isPrimitive()) {
            return false;
        }

        to = smashUpGeneric(to);

        return from.isAssignable(to) && from.getArrayDimensions() == to.getArrayDimensions();
    }

    private static String fixEnclosingClassName(Class<?> enclosing, Class<?> target) {
        Class<?> enclosingClass = enclosing.getEnclosingClass();

        String name = enclosingClass != null
                ? fixEnclosingClassName(enclosingClass, enclosing)
                : enclosing.getName();

        return name + "." + target.getSimpleName();
    }

    public static boolean isAssignable(ClassElement from, Class<?> to) {
        assert !to.isArray();

        // NB: primitives are considered assignable to boxed types since Micronaut 4.0.0
        if (from.isPrimitive() && !to.isPrimitive() || !from.isPrimitive() && to.isPrimitive()) {
            return false;
        }

        Class<?> enclosingClass = to.getEnclosingClass();

        if (to.getEnclosingClass() != null) {
            String name = fixEnclosingClassName(enclosingClass, to);

            return from.isAssignable(name) && from.getArrayDimensions() == 0;
        }

        return from.isAssignable(to) && from.getArrayDimensions() == 0;
    }

    public static String getSimpleName(ClassElement element) {
        String name = element.getName();

        if (!element.isArray()) {
            return name;
        }

        return name + "[]".repeat(element.getArrayDimensions());
    }

    public static boolean isPrimitive(ClassElement element) {
        return element.isPrimitive() && !element.isArray();
    }

    public static boolean isAccessibleFromFXMLFile(Element element) {
        return element.hasAnnotation(FXML_ANNOTATION_NAME);
    }

    public static PropertyElement fixProperty(PropertyElement element) {
        if (!element.getType().isGenericPlaceholder()) {
            return element;
        }

        // TODO: Is this fixed? Report upstream if not. Introduce version check if it is.
        // NB: Generic properties looks broken in Micronaut when inheritance involved. Get same property from owning
        //  type to fix this.
        ClassElement owningType = element.getOwningType();

        for (PropertyElement property : owningType.getBeanProperties()) {
            if (property.getName().equals(element.getName())) {
                return property;
            }
        }

        throw new AssertionError();
    }

    private ElementUtils() {
        super();
    }
}
