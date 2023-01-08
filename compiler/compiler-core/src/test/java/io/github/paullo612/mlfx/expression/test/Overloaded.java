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
package io.github.paullo612.mlfx.expression.test;

public class Overloaded {

    public OverloadType overload(int a) {
        return OverloadType.INT;
    }

    public OverloadType overload(double a) {
        return OverloadType.DOUBLE;
    }

    public OverloadType overload(long a) {
        return OverloadType.LONG;
    }

    public OverloadType overload(double[] a) {
        return OverloadType.DOUBLE_ARRAY;
    }

    public OverloadType overload(String a) {
        return OverloadType.STRING;
    }

    public OverloadType overload(int a, int b) {
        return OverloadType.INT_AND_INT;
    }

    public OverloadType overload(int a, long b) {
        return OverloadType.INT_AND_LONG;
    }

    public OverloadType overload(int a, String b) {
        return OverloadType.INT_AND_STRING;
    }

    public OverloadType overload(Double[] a, double b) {
        return OverloadType.DOUBLE_OBJECT_ARRAY_AND_DOUBLE;
    }

    public OverloadType overload(boolean[] a, boolean b) {
        return OverloadType.BOOLEAN_ARRAY_AND_BOOLEAN;
    }
}
