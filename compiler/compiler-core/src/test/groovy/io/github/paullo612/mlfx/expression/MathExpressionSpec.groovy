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
package io.github.paullo612.mlfx.expression

class MathExpressionSpec extends ExpressionSpec {

    def "Simple math (literal, literal => #ret): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a $op $b")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        ret     | a    | op  | b        | expectedResult
        'int'   | -3   | '+' | "-'-2'"  | -1
        'long'  | 3    | '+' | 3.0      | 6
        'long'  | 7.0  | '+' | -1       | 6
        'int'   | 7.0  | '+' | 2.0      | 9
        'short' | 10   | '-' | 6        | 4
        'long'  | 10   | '-' | 5.0      | 5
        'int'   | 3.0  | '-' | 4        | -1
        'long'  | -3.0 | '-' | 2.0      | -5
        'int'   | 3    | '*' | 1        | 3
        'long'  | 3    | '*' | 2.0      | 6
        'byte'  | 4.0  | '*' | 6        | 24
        'long'  | 4.0  | '*' | "'1.5'"  | 6
        'float' | 7    | '/' | 3        | 2
        'float' | 7    | '/' | "-'2.0'" | -3.5
        'long'  | 6.0  | '/' | 2        | 3
        'long'  | 6.0  | '/' | 3.0      | 2
        'int'   | 20   | '%' | 2        | 0
        'int'   | 20   | '%' | 3.0      | 2
        'int'   | 7.0  | '%' | 3        | 1
        'int'   | 7.0  | '%' | 2.0      | 1
    }

    def "Simple math (literal, #bType => #ret): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $bType b = $b;
    
    @Expression.Value("$a $op b")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        bType      | ret      | a       | op   | b             | expectedResult
        'long'     | 'int'    | 3       | '+'  | 2             | 5
        'int'      | 'String' | 28      | '+-' | 8             | '20'
        'Integer'  | 'long'   | "'3'"   | '-'  | 2             | 1
        'Integer'  | 'long'   | 42      | '--' | 40            | 82
        'int'      | 'long'   | 3.0     | '*'  | 2             | 6
        'String'   | 'long'   | 4.0     | '*-' | '"2"'         | -8
        'String'   | 'float'  | "'7.0'" | '/'  | '"2"'         | 3.5
        'Numbers'  | 'short'  | 14      | '/-' | 'Numbers.TWO' | -7
        'int'      | 'int'    | 7       | '%'  | 2             | 1
    }

    def "Simple math (#aType, literal => #ret): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    
    @Expression.Value("a $op $b")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType    | ret     | a     | op  | b       | expectedResult
        'long'   | 'int'   | 3     | '+' | "'2'"   | 5
        'String' | 'long'  | '"3"' | '-' | 2       | 1
        'int'    | 'long'  | 3     | '*' | 2.0     | 6
        'double' | 'float' | 7     | '/' | "'0.0'" | Double.POSITIVE_INFINITY
        'int'    | 'int'   | 7     | '%' | 2       | 1
    }

    def "Simple math (#aType, #bType => #ret): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    final $bType b = $b;
    
    @Expression.Value("a $op b")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType  | bType    | ret    | a | op  | b | expectedResult
        'long' | 'int'    | 'int'  | 3 | '+' | 2 | 5
        'short'| 'float'  | 'long' | 3 | '-' | 2 | 1
        'int'  | 'long'   | 'long' | 3 | '*' | 2 | 6
        'byte' | 'byte'   | 'byte' | 6 | '/' | 2 | 3
        'int'  | 'double' | 'int'  | 7 | '%' | 2 | 1
    }

    def "Math operator precedence: #expression == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final int a = 2;
    final int b = 2;
    final int c = 2;
    
    @Expression.Value("$expression")
    abstract int compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        expression    | expectedResult
        'a + b * c'   | 6
        'a / b + c'   | 3
        'a - b % c'   | 2
        '(a + b) * c' | 8
        'a * (b + c)' | 8
    }

    def "Simple math NC (literal, literal => int): #a #op #b fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a $op $b")
    abstract int compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        a           | op  | b          | expectedResult
        3           | '+' | 'true'     | MathContinuation.TYPE_ERROR
        3           | '+' | '-null'    | NegateContinuation.TYPE_ERROR
        'false'     | '-' | "'4'"      | MathContinuation.TYPE_ERROR
        7           | '*' | "'4.blah'" | MathContinuation.TYPE_ERROR
        '\\\"a\\\"' | '/' | 3          | MathContinuation.TYPE_ERROR
        3           | '/' | 0          | DivideContinuation.DIVISION_BY_ZERO_ERROR
        4           | '%' | 0          | RemainderContinuation.DIVISION_BY_ZERO_ERROR
    }

    def "Simple math NC (#aType, #bType => int): #a #op #b fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    final $aType a = $a;
    final $bType b = $b;
    
    @Expression.Value("a $op b")
    abstract int compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        aType    | bType    | a     | op  | b     | expectedResult
        'String' | 'Object' | '"a"' | '+' | '"b"' | MathContinuation.TYPE_ERROR
        'String' | 'Object' | '"a"' | '-' | '"b"' | MathContinuation.TYPE_ERROR
        'String' | 'Object' | '"a"' | '*' | '"b"' | MathContinuation.TYPE_ERROR
        'String' | 'Object' | '"a"' | '/' | '"b"' | MathContinuation.TYPE_ERROR
        'String' | 'Object' | '"a"' | '%' | '"b"' | MathContinuation.TYPE_ERROR
    }
}
