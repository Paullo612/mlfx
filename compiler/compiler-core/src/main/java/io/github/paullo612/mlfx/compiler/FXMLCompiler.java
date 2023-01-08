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

import io.github.paullo612.mlfx.api.ControllerAccessor;
import io.github.paullo612.mlfx.compiler.elements.CopyFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.DefineFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.ElementUtils;
import io.github.paullo612.mlfx.compiler.elements.FXMLElement;
import io.github.paullo612.mlfx.compiler.elements.IncludeFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.InstanceDeclarationFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.LoadableFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.ReadOnlyInstanceProperty;
import io.github.paullo612.mlfx.compiler.elements.ReadWriteInstanceProperty;
import io.github.paullo612.mlfx.compiler.elements.ReferenceFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.github.paullo612.mlfx.compiler.elements.RootFXMLElement;
import io.github.paullo612.mlfx.compiler.elements.StaticPropertyFXMLElement;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import javafx.fxml.FXMLLoader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Predicate;

// NB: We cannot generate FXML parser at ours compile time, as "valid elements and attributes are entirely dependent on
//  the current classpath". So, we'll use iterative parser here. Luckily, all necessary constants are public in
//  FXMLLoader. See JDK-8090834.
class FXMLCompiler {

    private static final String INITIALIZABLE_INTERFACE_NAME = "javafx.fxml.Initializable";
    private static final String INITIALIZE_METHOD_NAME = "initialize";

    interface Delegate extends CompilerContextImpl.Warner {

        OutputStream createClass(String name) throws IOException;
    }

    private final VisitorContext visitorContext;
    private final TaskFactory taskFactory;

    FXMLCompiler(VisitorContext visitorContext, TaskFactory taskFactory) {
        this.visitorContext = visitorContext;
        this.taskFactory = taskFactory;
    }

    private String getFXMLTrace(URL location, XMLStreamReader xmlStreamReader) {
        return "\n" + location + ":" + xmlStreamReader.getLocation().getLineNumber() + "\n";
    }

    private void handleProcessingInstruction(
            CompilerContextImpl context,
            String processingInstructionTarget,
            String processingInstructionData) {
        switch (processingInstructionTarget) {
            case FXMLLoader.IMPORT_PROCESSING_INSTRUCTION:
                context.handleImport(processingInstructionData);
                break;
            case ProcessingInstructions.MLFX_ROOT_TYPE:
                context.handleRootType(processingInstructionData);
                break;
            case ProcessingInstructions.MLFX_CONTROLLER_TYPE:
                context.handleControllerType(processingInstructionData);
                break;
        }
    }

    private FXMLElement<?> createFxNamespaceElement(CompilerContext context, FXMLElement<?> current, String localName) {
        switch (localName) {
            case FXMLLoader.INCLUDE_TAG:
                return new IncludeFXMLElement(current);
            case FXMLLoader.REFERENCE_TAG:
                return new ReferenceFXMLElement(current);
            case FXMLLoader.COPY_TAG:
                return new CopyFXMLElement(current);
            case FXMLLoader.ROOT_TAG:
                return new RootFXMLElement(current);
            case FXMLLoader.SCRIPT_TAG:
                throw context.compileError("Scripts are not supported yet.");
            case FXMLLoader.DEFINE_TAG:
                return new DefineFXMLElement(current);
            default:
                throw context.compileError(
                        FXMLLoader.FX_NAMESPACE_PREFIX + ":" + localName + " is not a valid element."
                );
        }
    }

    private FXMLElement<?> createGenericElement(CompilerContext context, FXMLElement<?> current, String localName) {
        // NB: The rule is that if element name starts with upper case later, then this is an instance declaration.
        //  If not, this is instance or static property, depending on prefix presence:
        //
        //  foo.bar.Baz      | fully qualified class name                   | instance declaration
        //  foo.Bar.Baz      | fully qualified inner class name             | instance declaration
        //  Baz              | class name                                   | instance declaration
        //  Bar.Baz          | inner class name                             | instance declaration
        //  name             | lower case word                              | instance property
        //  Baz.name         | class name with word postfix                 | static property
        //  foo.bar.Baz.name | fully qualified class name with word postfix | static property
        int i = localName.lastIndexOf('.');

        if (Character.isUpperCase(localName.charAt(i + 1))) {
            // Element name or its postfix starts with upper case letter. This is an instance declaration element.
            return context.getFXMLClassElement(localName)
                    .map(element -> new InstanceDeclarationFXMLElement(current, element))
                    .orElseThrow(() -> context.compileError(localName + " is not a valid type."));
        }

        // This is a property.
        String name = localName.substring(i + 1);

        // Property can't be a root element.
        if (current == null) {
            throw context.compileError("Invalid root element.");
        }

        LoadableFXMLElement<?> currentLoadable = current.asLoadableFXMLElement();

        if (currentLoadable == null) {
            // Properties only work with loadable parents.
            throw context.compileError("Parent element does not support property elements.");
        }

        if (i == -1) {
            // No prefixes or postfixes. This is an instance property. The only missing piece is a property itself.
            PropertyElement instanceProperty = currentLoadable.findProperty(name)
                    .orElseThrow(() -> context.compileError("Invalid property."));

            return instanceProperty.isReadOnly()
                    ? new ReadOnlyInstanceProperty(currentLoadable, instanceProperty)
                    : new ReadWriteInstanceProperty(currentLoadable, instanceProperty);
        }

        // Static property in all other cases.
        return context.getFXMLClassElement(localName.substring(0, i))
                .map(element -> new StaticPropertyFXMLElement(currentLoadable, name, element))
                .orElseThrow(() -> context.compileError(localName + " is not a valid property."));
    }

    private FXMLElement<?> createElement(
            CompilerContext context,
            FXMLElement<?> current,
            String prefix,
            String localName) {
        if (prefix == null || prefix.isEmpty()) {
            return createGenericElement(context, current, localName);
        } else if (prefix.equals(FXMLLoader.FX_NAMESPACE_PREFIX)) {
            return createFxNamespaceElement(context, current, localName);
        }

        throw context.compileError("Unexpected namespace prefix: " + prefix + ".");
    }

    private XMLStreamReader createParser(InputStream fxmlFile, Charset charset) {
        try {
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);

            InputStreamReader inputStreamReader = new InputStreamReader(fxmlFile, charset);
            return xmlInputFactory.createXMLStreamReader(inputStreamReader);
        } catch (XMLStreamException exception) {
            throw new CompileErrorException(exception);
        }
    }

    private void initializeControllerWithInterfaceMethod(
            CompilerContext context,
            ExpressionContext.Loadable controller) {
        ExpressionContext.Loadable location = context.getScope().get(FXMLLoader.LOCATION_KEY);
        ExpressionContext.Loadable nonRequiredResourceBundle = context.getNonRequiredResourceBundle();

        context.getRenderer().render(methodVisitor -> {
            controller.load().render(methodVisitor);
            location.load().render(methodVisitor);
            nonRequiredResourceBundle.load().render(methodVisitor);

            // NB: We know actual controller type (it must be a class), so, invokevirtual is OK here.
            methodVisitor.invokeVirtual(
                    RenderUtils.type(controller.getClassElement()),
                    new Method(
                            INITIALIZE_METHOD_NAME,
                            "(" + RenderUtils.type(location.getClassElement()) + RootRenderer.RESOURCE_BUNDLE_D + ")V"
                    )
            );
        });
    }

    private void initializeControllerWithControllerMethod(
            CompilerContext context,
            ExpressionContext.Loadable controller) {
        // Set location and resource bundle.
        context.setControllerField(FXMLLoader.LOCATION_KEY, context.getScope().get(FXMLLoader.LOCATION_KEY));
        context.setControllerField(FXMLLoader.RESOURCES_KEY, context.getNonRequiredResourceBundle());

        // Search for no-args initialize method, and call one, if any.
        controller.getClassElement().getEnclosedElement(ElementQuery.ALL_METHODS
                .onlyInstance()
                .named(Predicate.isEqual(INITIALIZE_METHOD_NAME))
                .filter(m -> m.getParameters().length == 0)
        )
                .ifPresent(method -> {
                    if (!method.isReflectionRequired(context.getTargetType())) {
                        context.getRenderer().render(methodVisitor -> {
                            controller.load().render(methodVisitor);
                            RenderUtils.renderMethodCall(methodVisitor, method);
                        });
                        return;
                    }

                    if (!ElementUtils.isAccessibleFromFXMLFile(method)) {
                        // We've found initialize method, but it is not marked as accessible from FXML document.
                        //  Nothing to do here.
                        context.warn(
                                "There is \"initialize\" method in controller that cannot be called because it is not"
                                        + " accessible and not annotated by @FXML annotation.",
                                method
                        );
                        return;
                    }

                    // Delegate to accessor.
                    context.getRenderer().render(methodVisitor -> {
                        context.getControllerAccessor().load().render(methodVisitor);

                        controller.load().render(methodVisitor);
                        methodVisitor.push(method.getName());

                        // Empty object array
                        methodVisitor.push(0);
                        methodVisitor.newArray(Type.getType(Object.class));

                        methodVisitor.invokeInterface(
                                Type.getType(ControllerAccessor.class),
                                new Method(
                                        "executeMethod",
                                        "(" + RenderUtils.OBJECT_D + RenderUtils.STRING_D
                                                + Type.getType(Object[].class).getDescriptor() + ")V"
                                )
                        );
                    });

                });
    }

    private void initializeController(CompilerContext context) {
        ExpressionContext.Loadable controller = context.getScope().get(FXMLLoader.CONTROLLER_KEYWORD);

        if (controller == null) {
            // No controller present, so, nothing to initialize.
            return;
        }

        // Check if controller implements Initializable from javafx-fxml. Do this with care, as we do not have this
        //  module on classpath at runtime.
        visitorContext.getClassElement(INITIALIZABLE_INTERFACE_NAME)
                .filter(c -> ElementUtils.isAssignable(controller.getClassElement(), c))
                .ifPresentOrElse(
                        __ -> initializeControllerWithInterfaceMethod(context, controller),
                        () -> initializeControllerWithControllerMethod(context, controller)
                );
    }

    private void handleParserEvent(
            CompilerContextImpl context,
            RootRenderer renderer,
            XMLStreamReader xmlStreamReader,
            int event) {
        FXMLElement<?> current = context.getCurrentFXMLElement();

        switch (event) {
            case XMLStreamConstants.PROCESSING_INSTRUCTION: {
                String processingInstructionTarget = Optional.ofNullable(xmlStreamReader.getPITarget())
                        .map(String::trim)
                        .orElse("");
                String processingInstructionData = Optional.ofNullable(xmlStreamReader.getPIData())
                        .map(String::trim)
                        .orElse(null);

                handleProcessingInstruction(context, processingInstructionTarget, processingInstructionData);
                break;
            }
            case XMLStreamConstants.START_ELEMENT: {
                String prefix = xmlStreamReader.getPrefix();
                String localName = xmlStreamReader.getLocalName();

                FXMLElement<?> element = createElement(context, current, prefix, localName);

                element.initialize(context);

                if (element.requiresAttributesLookahead()) {
                    for (int i = 0; i < xmlStreamReader.getAttributeCount(); ++i) {
                        element.handleAttributeLookahead(
                                context,
                                xmlStreamReader.getAttributePrefix(i),
                                xmlStreamReader.getAttributeLocalName(i),
                                xmlStreamReader.getAttributeValue(i)
                        );
                    }
                }

                if (current == null) {
                    boolean hasFxRoot = context.hasFxRoot();
                    ExpressionContext.Loadable rootLoadable = context.getRootLoadable();

                    if (rootLoadable != null && !hasFxRoot) {
                        throw context.compileError(
                                ProcessingInstructions.MLFX_ROOT_TYPE
                                        + " processing instruction defined, but no " +
                                        FXMLLoader.FX_NAMESPACE_PREFIX + ":" + FXMLLoader.ROOT_TAG + " present."
                        );
                    }

                    ClassElement controllerClassElement =
                            Optional.ofNullable(context.getScope().get(FXMLLoader.CONTROLLER_KEYWORD))
                                    .map(ExpressionContext.Loadable::getClassElement)
                                    .orElse(null);

                    boolean hasController = controllerClassElement != null;

                    if (controllerClassElement == null) {
                        controllerClassElement = context.getClassElement(Object.class);
                    }

                    LoadableFXMLElement<?> loadableFXMLElement = element.asLoadableFXMLElement();
                    assert loadableFXMLElement != null;

                    renderer.initialize(
                            context.getTargetType(),
                            loadableFXMLElement.getClassElement(),
                            controllerClassElement,
                            hasFxRoot,
                            hasController,
                            context.requiresExternalController()
                    );
                }

                if (element.requiresAttributesLookahead()) {
                    element.handleAttributesLookaheadFinish(context);
                }

                // Push new FXMLElement to the stack
                context.setCurrentFXMLElement(element);

                for (int i = 0; i < xmlStreamReader.getAttributeCount(); ++i) {
                    element.handleAttribute(
                            context,
                            xmlStreamReader.getAttributePrefix(i),
                            xmlStreamReader.getAttributeLocalName(i),
                            xmlStreamReader.getAttributeValue(i)
                    );
                }

                element.handleAttributesFinish(context);

                break;
            }
            case XMLStreamConstants.END_ELEMENT: {
                current.handleEndElement(context);

                // Pop FXMLElement from the stack
                context.setCurrentFXMLElement(current.getParent());
                break;
            }
            case XMLStreamConstants.CHARACTERS: {
                if (xmlStreamReader.isWhiteSpace()) {
                    // Not interested in whitespaces.
                    break;
                }

                String text = xmlStreamReader.getText();
                current.handleCharacters(context, text);

                break;
            }
        }
    }

    private CompileTask.CompiledFXMLLoaderReference doCompile(
            URL location,
            Charset charset,
            InputStream fxmlFile,
            ClassElement targetType,
            Delegate delegate) {
        String fxmlFileName = location.getPath();

        int lastSlashIndex = fxmlFileName.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            fxmlFileName = fxmlFileName.substring(lastSlashIndex + 1);
        }

        XMLStreamReader xmlStreamReader = createParser(fxmlFile, charset);
        RootRenderer renderer = new RootRenderer(fxmlFileName);

        CompilerContextImpl context =
                new CompilerContextImpl(visitorContext, taskFactory, delegate, targetType, charset, renderer);

        // GO !
        try {
            while (xmlStreamReader.hasNext()) {
                handleParserEvent(context, renderer, xmlStreamReader, xmlStreamReader.next());
            }

            initializeController(context);
        } catch (XMLStreamException | CompileErrorException e) {
            // Attach FXML trace.
            throw new CompileErrorException(e.getMessage() + getFXMLTrace(location, xmlStreamReader), e);
        }

        for (BindingExpressionRendererImpl expressionRenderer : context.getExpressionRenderers()) {
            renderer.addInnerClass(expressionRenderer.getInternalClassName(), expressionRenderer.getClassName());
            createClass(
                    delegate,
                    expressionRenderer.getInternalClassName().replace('/', '.'),
                    expressionRenderer.dispose()
            );
        }

        // Get root and controller class elements before root renderer disposal.
        ClassElement rootClassElement = renderer.getRootClassElement();
        ClassElement controllerClassElement = renderer.getControllerClassElement();

        createClass(delegate, targetType.getName(), renderer.dispose());

        // NB: It would be better to return just compiled class element obtained through visitor context, and collect
        //  all needed information from it. But javac bites us here. Filer#createClassFile checks file existence before
        //  giving us OutputStream to write to. So, just compiled class is cached as nonexistent inside javac internals
        //  at this point. Best we can do here is to return target type stub and root node's class element directly.
        return new CompileTask.CompiledFXMLLoaderReference() {

            @Override
            public ClassElement getTargetType() {
                return targetType;
            }

            @Override
            public ClassElement getRootClassElement() {
                return rootClassElement;
            }

            @Override
            public ClassElement getControllerClassElement() {
                return controllerClassElement;
            }
        };
    }

    CompileTask.CompiledFXMLLoaderReference compile(
            URL url,
            Charset charset,
            ClassElement targetType,
            Delegate delegate) {
        try (InputStream fxmlFile = url.openStream()) {
            return doCompile(url, charset, fxmlFile, targetType, delegate);
        } catch (IOException e) {
            throw new CompileErrorException("Failed to open FXML file " + url + ": " + e.getMessage(), e);
        }
    }

    private void createClass(Delegate delegate, String name, byte[] data) {
        try (OutputStream outputStream = delegate.createClass(name)) {
            outputStream.write(data);
        } catch (IOException e) {
            throw new CompileErrorException("Failed to define class: " + e.getMessage(), e);
        }
    }
}
