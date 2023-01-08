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
package io.github.paullo612.mlfx.compiler;

import io.github.paullo612.mlfx.api.CompileFXML;
import io.github.paullo612.mlfx.api.CompiledFXMLLoader;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompileFXMLVisitor implements TypeElementVisitor<CompileFXML, Object> {

    private static boolean MICRONAUT_VERSION_CHECK_DONE;

    public static final int ORDER = 100;

    static final String RESOURCES_DIRECTORY_OPTION = VisitorContext.MICRONAUT_BASE_OPTION_NAME
            + ".mlfx.resourcesDirectory";

    private static final String FXML_DIRECTORIES_MEMBER = "fxmlDirectories";
    private static final String CHARSET_MEMBER = "charset";

    private static final String RESOURCES_DIRECTORY = "src/main/resources";
    static final String FXML_EXTENSION = ".fxml";

    static String computeClassName(String className) {
        // Capitalize class name. Micronaut's AST implementation thinks that element is subpackage if element
        //  name does not start from capital letter.
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            StringBuilder name = new StringBuilder(NameUtils.capitalize(className.substring(lastDotIndex + 1)));

            for (int i = 0; i < name.length(); ++i) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                    name.setCharAt(i, '_');
                }
            }

            className = className.substring(0, lastDotIndex + 1) + "$" + name;
        }

        className += "$" + CompiledFXMLLoader.class.getSimpleName();

        return className;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!MICRONAUT_VERSION_CHECK_DONE) {
            if (!VersionUtils.isAtLeastMicronautVersion(VersionConstants.MICRONAUT_MINIMUM_VERSION)) {
                context.fail(
                        "micronaut-inject-<your_favourite_language> on annotation processor path must be at least "
                                + VersionConstants.MICRONAUT_MINIMUM_VERSION,
                        element
                );
            }

            MICRONAUT_VERSION_CHECK_DONE = true;
        }

        AnnotationMetadata metadata = element.getAnnotationMetadata();

        if (!metadata.hasDeclaredAnnotation(CompileFXML.class)) {
            // Do not process inner classes.
            return;
        }

        String[] directories = metadata.stringValues(CompileFXML.class, FXML_DIRECTORIES_MEMBER);

        if (directories.length == 0) {
            context.warn("Found @CompileFXML without any FXML directories specified, skipping.", element);
            return;
        }

        String charsetString = metadata.stringValue(CompileFXML.class, CHARSET_MEMBER)
                .orElseGet(() -> metadata.getDefaultValue(CompileFXML.class, CHARSET_MEMBER, String.class)
                        .orElse("")
                );

        Charset charset;

        try {
            charset = Charset.forName(charsetString);
        } catch (IllegalCharsetNameException e) {
            context.fail("Failed to parse charset name \"" + charsetString + "\".", element);
            return;
        } catch (UnsupportedCharsetException e) {
            context.fail("Charset \"" + charsetString + "\" is not supported.", element);
            return;
        }

        Path projectDirectory = context.getProjectDir()
                .orElse(null);

        if (projectDirectory == null) {
            context.fail("Failed to get project directory.", element);
            return;
        }

        String separator = projectDirectory.getFileSystem().getSeparator();

        String resourcesDirectory = context.getOptions().getOrDefault(RESOURCES_DIRECTORY_OPTION, RESOURCES_DIRECTORY)
                .replace("/", separator);

        Path resourcesPath = projectDirectory.resolve(resourcesDirectory);

        if (!Files.isDirectory(resourcesPath)) {
            context.warn(
                    "Project's resources directory (" + resourcesPath.toAbsolutePath() + ") does not exist, skipping.",
                    element
            );
            return;
        }

        FXMLCompiler.Delegate compilerDelegate = new FXMLCompiler.Delegate() {

            @Override
            public OutputStream createClass(String name) throws IOException {
                return context.visitClass(name, element);
            }

            @Override
            public void warn(String message) {
                context.warn(message, element);
            }
        };

        TaskFactory taskFactory =
                new TaskFactory(context, resourcesPath.toUri(), compilerDelegate);

        List<CompileTask> tasks = new ArrayList<>();

        for (String directory : directories) {
            while (directory.startsWith("/")) {
                directory = directory.substring(1);
            }

            String directorySubPath = directory.replace("/", separator);

            Path directoryPath = resourcesPath.resolve(directorySubPath);

            if (!Files.isDirectory(directoryPath)) {
                context.warn(
                        "FXML directory " + directoryPath.toAbsolutePath() + " does not exist, skipping.", element
                );
                continue;
            }

            List<Path> fxmlFiles;

            try (Stream<Path> stream = Files.walk(directoryPath)) {
                fxmlFiles = stream
                        .filter(file -> !Files.isDirectory(file))
                        .filter(file -> file.toString().endsWith(FXML_EXTENSION))
                        .sorted(Comparator.comparing(Path::toString))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                context.fail("Failed to traverse FXML directory " + directory + ": " + e.getMessage(), element);
                continue;
            }

            if (fxmlFiles.isEmpty()) {
                context.info("No FXML files in FXML directory " + directory, element);
                continue;
            }


            for (Path file : fxmlFiles) {
                Path relativeFilePath = resourcesPath.relativize(file);
                String className = relativeFilePath.toString().replace(separator, ".");

                if (className.length() < FXML_EXTENSION.length() + 1) {
                    context.fail("Failed to compute class name for FXML file " + file, element);
                    return;
                }

                className = className.substring(0, className.length() - FXML_EXTENSION.length());
                className = computeClassName(className);

                ClassElement targetClass = ClassElement.of(className);
                tasks.add(taskFactory.registerTask(URI.create(relativeFilePath.toString()), targetClass));
            }
        }

        for (CompileTask task : tasks) {
            try {
                task.compile(charset);
            } catch (CompileErrorException e) {
                context.fail(e.getMessage(), element);
                return;
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(CompileFXML.class.getName());
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton(RESOURCES_DIRECTORY_OPTION);
    }
}
