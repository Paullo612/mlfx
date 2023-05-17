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
            <version>0.5.0</version>
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
                    <annotationProcessorPaths>
                        <path>
                            <groupId>io.micronaut</groupId>
                            <artifactId>micronaut-inject-java</artifactId>
                            <version>3.4.0</version>
                        </path>
                        <path>
                            <groupId>io.github.paullo612.mlfx.compiler</groupId>
                            <artifactId>micronaut</artifactId>
                            <version>0.5.0</version>
                        </path>
                        ...
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            ...
        </plugins>
        ...
    </build>
    ...
</project>

```

## Supported annotation processor options

`micronaut.mlfx.resourcesDirectory` specifies base directory where to search for fxml files. Points to project's
resources directory by default. 