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

class ComparisonExpressionSpec extends ExpressionSpec {

    def "Simple comparison (literal, literal => boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a $op $b")
    abstract boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        a       | op   | b         | expectedResult
        3       | '>'  | "'2'"     | true
        3       | '>'  | 2.0       | true
        7.0     | '>'  | -1        | true
        7.0     | '>'  | 2.0       | true
        -3      | '>'  | "'2'"     | false
        -3      | '>'  | 2.0       | false
        -7.0    | '>'  | -1        | false
        -7.0    | '>'  | 2.0       | false
        10      | '>=' | 10        | true
        10      | '>=' | 5.0       | true
        3.0     | '>=' | 2         | true
        3.0     | '>=' | 2.0       | true
        -10     | '>=' | 10        | false
        -10     | '>=' | 5.0       | false
        -3.0    | '>=' | 2         | false
        -3.0    | '>=' | 2.0       | false
        3       | '<'  | 4         | true
        3       | '<'  | 5.0       | true
        4.0     | '<'  | 6         | true
        4.0     | '<'  | "'4.5'"   | true
        3       | '<'  | -4        | false
        3       | '<'  | -5.0      | false
        4.0     | '<'  | -6        | false
        4.0     | '<'  | "'-4.5'"  | false
        7       | '<=' | 7         | true
        -7      | '<=' | "-'2.0'"  | true
        -6.0    | '<=' | 2         | true
        -6.0    | '<=' | 3.0       | true
        7       | '<=' | 6         | false
        7       | '<=' | "-'2.0'"  | false
        6.0     | '<=' | 2         | false
        6.0     | '<=' | 3.0       | false
    }

    def "Simple comparison (literal, #bType => Boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $bType b = $b;
    
    @Expression.Value("$a $op b")
    abstract Boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        bType      | a       | op    | b             | expectedResult
        'long'     | 3       | '>'   | 2             | true
        'short'    | 28      | '>-'  | 8             | true
        'double'   | "'3'"   | '<'   | 2             | false
        'Integer'  | 42      | '<-'  | 40            | false
        'float'    | 3       | '>='  | '2.0f'        | true
        'String'   | 4.0     | '>=-' | '"2"'         | true
        'String'   | "'7.0'" | '<='  | '"2"'         | false
        'Numbers'  | 14      | '<=-' | 'Numbers.TWO' | false
    }

    def "Simple comparison (#aType, literal => boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    
    @Expression.Value("a $op $b")
    abstract boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType     | a     | op   | b       | expectedResult
        'long'    | 3     | '>'  | "'2'"   | true
        'String'  | '"3"' | '<'  | 2       | false
        'int'     | 3     | '<=' | 3.0     | true
        'double'  | '0.0' | '>=' | 7       | false
    }

    def "Simple comparison (#aType, #bType => Boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    final $bType b = $b;
    
    @Expression.Value("a $op b")
    abstract Boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType     | bType     | a             | op   | b             | expectedResult
        'long'    | 'int'     | 3             | '>'  | 2             | true
        'short'   | 'float'   | 3             | '<'  | 2             | false
        'int'     | 'long'    | 3             | '>=' | 2             | true
        'byte'    | 'byte'    | 6             | '<=' | 2             | false
    }

    def "Simple comparison NC (literal, literal => boolean): #a #op #b fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a $op $b")
    abstract boolean compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        a           | op   | b         | expectedResult
        3           | '>'  | "'foo'"   | NumberComparisonContinuation.TYPE_ERROR
        4           | '<'  | "!1"      | NotContinuation.TYPE_ERROR
        4           | '>=' | "-true"   | NegateContinuation.TYPE_ERROR
        "'4.foo'"   | '<=' | 4         | NumberComparisonContinuation.TYPE_ERROR
        "'true'"    | '<=' | "'false'" | NumberComparisonContinuation.TYPE_ERROR
        true        | '>'  | false     | NumberComparisonContinuation.TYPE_ERROR
    }

    def "Simple comparison NC (#aType, #bType => boolean): #a #op #b fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    final $aType a = $a;
    final $bType b = $b;
    
    @Expression.Value("a $op b")
    abstract boolean compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        aType     | bType    | a     | op   | b        | expectedResult
        'String'  | 'Object' | '"a"' | '>'  | '"b"'    | NumberComparisonContinuation.TYPE_ERROR
        'String'  | 'Object' | '"a"' | '<'  | '"b"'    | NumberComparisonContinuation.TYPE_ERROR
        'String'  | 'Object' | '"a"' | '>=' | '"b"'    | NumberComparisonContinuation.TYPE_ERROR
        'String'  | 'Object' | '"a"' | '<=' | '"b"'    | NumberComparisonContinuation.TYPE_ERROR
    }
}
