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
package io.github.paullo612.mlfx.compiler

import io.github.paullo612.mlfx.api.CompiledFXMLLoader
import io.github.paullo612.mlfx.api.CompiledLoadException
import io.github.paullo612.mlfx.api.ControllerAccessor
import io.github.paullo612.mlfx.api.Result
import javafx.fxml.FXMLLoader
import org.opentest4j.AssertionFailedError

import javax.tools.JavaFileObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.Stream

class ComplianceSpec extends CompileSpec {

    private static final String PROPERTIES_EXTENSION = '.properties'
    private static final String CLASS_EXTENSION = JavaFileObject.Kind.CLASS.extension

    private static class PathAndTestName {

        final Path path
        final String testName

        PathAndTestName(Path path, String testName) {
            this.path = path
            this.testName = testName
        }
    }

    private static class ControllerAccessorImpl<C> implements ControllerAccessor<C> {

        private final Class<C> controllerClass

        ControllerAccessorImpl(Class<C> controllerClass) {
            this.controllerClass = controllerClass
        }

        @Override
        C newControllerInstance() {
            controllerClass.getDeclaredConstructor().newInstance()
        }

        @Override
        void setField(C controller, String fieldName, Object value) {
            controller[fieldName] = value
        }

        @Override
        void executeMethod(C controller, String methodName, Object... arguments) {
            List<Object> args = Arrays.asList(arguments)

            controller."$methodName"(args)
        }

        @Override
        ExecutableMethod findMethod(C controller, String methodName, Class<?>... argumentTypes)
                throws CompiledLoadException {
            def methods = controller.getMetaClass().respondsTo(controller, methodName, argumentTypes)

            if (methods.isEmpty() || methods.size() > 1) {
                throw new CompiledLoadException("Unable to find method $methodName in class $controller.class.name")
            }

            new ExecutableMethod() {

                @Override
                void execute(Object... arguments) {
                    methods[0].doMethodInvoke(controller, arguments)
                }
            }
        }

        @Override
        Class<C> getControllerClass() {
            controllerClass
        }
    }

    private static class LoadResult {

        private final Object root
        private final Object controller

        LoadResult(Object root, Object controller) {
            this.root = root
            this.controller = controller
        }

        boolean equals(o) {
            if (this.is(o)) {
                return true
            }

            if (o == null || getClass() != o.class) {
                return false
            }

            LoadResult that = (LoadResult) o

            return (root == that.root) && (controller == that.controller)
        }

        int hashCode() {
            int result
            result = root.hashCode()
            result = 31 * result + (controller != null ? controller.hashCode() : 0)
            return result
        }

        @Override
        String toString() {
            String result = root.toString()

            if (controller != null) {
                result += ",\n" + controller.toString()
            }

            return result
        }
    }

    private static List<PathAndTestName> collectTests() {
        URL url = ComplianceSpec.class.getResource('/io/github/paullo612/mlfx/compiler/compliance/')
        assert url != null

        Path rootPath = Paths.get(url.toURI())

        try (Stream<Path> stream = Files.list(rootPath)) {
            stream
                    .filter(file -> Files.isDirectory(file))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(file -> new PathAndTestName(file, file.getFileName().toString()))
                    .collect(Collectors.toList())
        }
    }

    private static List<String> collectFiles(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(file -> file.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(CompileFXMLVisitor.FXML_EXTENSION))
                    .sorted()
                    .collect(Collectors.toList())
        } catch (IOException e) {
            throw new AssertionFailedError(e.getMessage())
        }
    }

    private static Object resolveRoot(Path workingDirectory, Path path, String fxmlFile) {
        String classFileName = fxmlFile.capitalize()
                .substring(0, fxmlFile.length() - CompileFXMLVisitor.FXML_EXTENSION.length()) + 'Root' + CLASS_EXTENSION

        Path classPath = path.resolve(classFileName)
        if (!Files.isRegularFile(classPath)) {
            return null
        }

        String className = workingDirectory
                .relativize(classPath)
                .toString()
                .replace('/', '.')

        className = className.substring(0, className.length() - CLASS_EXTENSION.length())

        Class.forName(className).getDeclaredConstructor().newInstance()
    }

    private static Object resolveController(Path workingDirectory, Path path, String fxmlFile) {
        String classFileName = fxmlFile.capitalize().substring(
                0, fxmlFile.length() - CompileFXMLVisitor.FXML_EXTENSION.length()
        ) + 'Controller' + CLASS_EXTENSION

        Path classPath = path.resolve(classFileName)
        if (!Files.isRegularFile(classPath)) {
            return null
        }

        String className = workingDirectory
                .relativize(classPath)
                .toString()
                .replace('/', '.')

        className = className.substring(0, className.length() - CLASS_EXTENSION.length())

        Class.forName(className).getDeclaredConstructor().newInstance()
    }

    private static ResourceBundle resolveResourceBundle(Path workingDirectory, Path path, String fxmlFile) {
        String resourceBundleFileName =
                fxmlFile.substring(0, fxmlFile.length() - CompileFXMLVisitor.FXML_EXTENSION.length()) + '.properties'

        Path resourceBundlePath = path.resolve(resourceBundleFileName)
        if (!Files.isRegularFile(resourceBundlePath)) {
            return null
        }

        String resourceBundleName = workingDirectory
                .relativize(resourceBundlePath)
                .toString()
                .replace('/', '.')

        resourceBundleName =
                resourceBundleName.substring(0, resourceBundleName.length() - PROPERTIES_EXTENSION.length())

        ResourceBundle.getBundle(resourceBundleName)
    }

    private static LoadResult loadUsingCompiledFXMLLoader(
            Path workingDirectory,
            Path fxmlFile,
            Object root,
            Object controller,
            ResourceBundle resourceBundle) {
        String fxmlFileResourcePath = workingDirectory.relativize(fxmlFile).toString()
        String loaderClassName = fxmlFileResourcePath.substring(
                0, fxmlFileResourcePath.length() - CompileFXMLVisitor.FXML_EXTENSION.size()
        )
        loaderClassName = loaderClassName.replace('/', '.')
        loaderClassName = CompileFXMLVisitor.computeClassName(loaderClassName)

        Class<?> loaderClass = Class.forName(loaderClassName)
        Class<? extends CompiledFXMLLoader> compiledLoaderClass = loaderClass.asSubclass(CompiledFXMLLoader.class)

        CompiledFXMLLoader<?, ?> loader = compiledLoaderClass.getDeclaredConstructor().newInstance()
        Result<?, ?> result =
                loader.load(c -> new ControllerAccessorImpl<>(c), controller, root, resourceBundle)

        new LoadResult(result.rootInstance, result.controller)
    }

    private static LoadResult loadUsingFXMLLoader(
            Path workingDirectory,
            Path fxmlFile,
            Object root,
            Object controller,
            ResourceBundle resourceBundle) {
        String fxmlFileResourcePath = workingDirectory.relativize(fxmlFile).toString()

        // NB: Use |ComplianceSpec.class.getResource(...)| to get FXML file URL. Using |fxmlFile.toUri().toURL()| may
        //  produce URL encoded escape sequences in different case (in case there are non-ASCII symbols in path
        //  from where tests are executed, %AB vs %ab, this is true for GraalVM 22.0.0.2 at least), which will
        //  cause tests failures.
        URL fxmlUrl = ComplianceSpec.class.getResource('/' + fxmlFileResourcePath)
        assert fxmlUrl != null

        FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl, resourceBundle)

        if (root != null) {
            fxmlLoader.setRoot(root)
        }

        if (controller != null) {
            fxmlLoader.setController(controller)
        }

        Object rootObject = fxmlLoader.load()
        Object controllerObject = fxmlLoader.controller

        new LoadResult(rootObject, controllerObject)
    }

    def "FXMLLoader loads the same object tree as CompiledFXMLLoader for #pathAndTestName.testName"() {
        when:
        CompileResult compileResult = compile(pathAndTestName.path)

        then:
        Boolean.TRUE == compileResult.result
        compileResult.diagnostics.isEmpty()

        when:
        Path path = pathAndTestName.path

        List<String> files = collectFiles(path)

        List<LoadResult> compiledFXMLLoaderResults = []
        List<LoadResult> FXMLLoaderResults = []

        Path workingDirectory = TEST_CLASSES_OUTPUT_PATH.toPath().toAbsolutePath()

        for (String file : files) {
            Path fxmlFile = path.resolve(file)

            Object root = resolveRoot(workingDirectory, path, file)
            Object controller = resolveController(workingDirectory, path, file)
            ResourceBundle resourceBundle = resolveResourceBundle(workingDirectory, path, file)

            compiledFXMLLoaderResults
                    << loadUsingCompiledFXMLLoader(workingDirectory, fxmlFile, root, controller, resourceBundle)

            FXMLLoaderResults << loadUsingFXMLLoader(workingDirectory, fxmlFile, root, controller, resourceBundle)
        }

        then:
        compiledFXMLLoaderResults == FXMLLoaderResults

        where:
        pathAndTestName << collectTests()
    }
}
