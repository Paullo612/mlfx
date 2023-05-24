# mlfx

## What is this?

An attempt to implement a compiler for [OpenJFX](https://openjfx.io/)'s FXML language.

## Motivation

Now, in a world where OpenJFX application can be compiled to native executable using GraalVM 
native-image tool, it is important not to use Java reflections. Running tracing agent on each release and clicking over
all interface is a major pain. This project provides a way to compile FXML files ahead of time, so, reflection configs
for native image are not needed any more (at least for part of application loading FXML). Ahead of time compilation also
speeds up UI loading.

## Goals

* Stay compatible with Scene Builder tool.
* Minimize reflections use in generated code.
* Provide API to mimic `javafx-fxml`'s FXMLLoader class for easier migration.

## Not a goals

* Stay compatible with FXML's binding expression syntax (now, for example, binding expressions language implementation 
supports method calls, which is not supported by `javafx-fxml`).
* Builders support.

## What is not (yet) implemented?

* Scripts support.
* Incremental compilation is completely broken.
* Compiling arbitrary fxml files from compile classpath. For example, if you `fx:include`'ing something from dependency 
jar, you must compile this dependency using mlfx too.
* No-arg controller methods as event handlers. You have to add event argument to controller method used as event handler
even if argument is not used. mlfx uses `invokedynamic` JVM instruction to implement `javafx.event.EventHandler` at
compile time, so, event handler becomes a controller method reference in Java terms. Allowing no-arg methods as event
handlers would require generating static trampoline function for each handler to make it work.

## How to use

MLFX is designed to work with backends that provide reflective access to controller fields and methods. Currently, there
is only [Micronaut® framework](https://micronaut.io/) based backend implemented. Micronaut version should be at least
3.4.0.

Use `@CompileFXML` annotation to specify where to search for fxml files. Files found will be compiled ahead of time. Use
`MLFXLoader` as direct `FXMLLoader` replacement.

Add following requirements to `module-info.java`:
```java
module my.app {
    ...
    requires io.micronaut.core;
    requires io.micronaut.inject;
    requires io.github.paullo612.mlfx.api.core;
    ...
}
```

### Maven
Add dependency to API module and add compiler to annotation processor path. For example:

```xml

<project>
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>io.github.paullo612.mlfx.api</groupId>
            <artifactId>micronaut</artifactId>
            <version>0.6.1</version>
        </dependency>
        ...
    </dependencies>
    ...
    <build>
        ...
        <plugins>
            ...
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    ...
                    <!-- Incremental compilation is completely broken in mlfx, so, recompile whole module on any change 
                         in fxml files. -->
                    <fileExtensions>
                        <fileExtension>class</fileExtension>
                        <fileExtension>jar</fileExtension>
                        <fileExtension>fxml</fileExtension>
                    </fileExtensions>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>io.micronaut</groupId>
                            <artifactId>micronaut-inject-java</artifactId>
                            <version>3.4.0</version>
                        </path>
                        <path>
                            <groupId>io.github.paullo612.mlfx.compiler</groupId>
                            <artifactId>micronaut</artifactId>
                            <version>0.6.1</version>
                        </path>
                        ...
                    </annotationProcessorPaths>
                    ...
                </configuration>
            </plugin>
            ...
        </plugins>
        ...
    </build>
    ...
</project>

```
See [sample Maven project](https://github.com/Paullo612/mlfx-sample), last commit adds mlfx support to it.

## Processing instructions

There are two new processing instructions that mlfx may need. Those are `mlfxControllerType` and `mlfxRootType`. mlfx
compiles ahead of time, so, it must know actual controller and root types when compiling. Consider following example:
```xml
<Car xmlns="http://javafx.com/javafx/19.0.0">
    <Engine manufacturer="$controller.foo.bar.baz"/>
</Car>
```
There is controller reference, but what is actual controller type? Has it `foo` property? What is `foo` property type?
So, to make it compile, if your controller is of `com.acme.CarController` type, you have to add processing instruction
to your fxml file:
```xml
<?mlfxControllerType com.acme.CarController?>
<Car xmlns="http://javafx.com/javafx/19.0.0">
    <Engine manufacturer="$controller.foo.bar.baz"/>
</Car>
```
Then you can pass `com.acme.CarController` instance to `MLFXLoader` through `setController` method when loading.

When controller is specified on root element, processing instruction is not needed:
```xml
<Car 
        xmlns="http://javafx.com/javafx/19.0.0" 
        xmlns:fx="http://javafx.com/fxml/1"
        fx:controller="com.acme.CarController"
>
    <Engine manufacturer="$controller.foo.bar.baz"/>
</Car>
```
This will compile fine without any special processing instructions.

Same applies to `fx:root` elements. There is no attribute to express actual root type in `fx:` namespace, so processing
instruction is mandatory in this case. If actual component class is `com.acme.MusculeCar`, you can express it in FXML
like this:
```xml
<?mlfxRootType com.acme.MusculeCar?>
<fx:root 
        xmlns="http://javafx.com/javafx/19.0.0" 
        xmlns:fx="http://javafx.com/fxml/1"
        type="Car"
>
    <Engine manufacturer="$propertyOfMuscleCar.bar.baz"/>
</fx:root>
```

## Supported annotation processor options

`micronaut.mlfx.resourcesDirectory` specifies base directory where to search for fxml files. Points to project's
resources directory by default. 
