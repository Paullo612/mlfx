package io.github.paullo612.mlfx.compiler.test;

import java.util.Objects;

public class Trunk {

    Wheel spareWheel;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Trunk other = (Trunk) o;

        return Objects.equals(spareWheel, other.spareWheel);
    }

    @Override
    public String toString() {
        return "Trunk { spareWheel = " + spareWheel + "}";
    }
}
