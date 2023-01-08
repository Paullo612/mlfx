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

import org.codehaus.groovy.syntax.Numbers

class EqualityExpressionSpec extends ExpressionSpec {

    def "Simple equality (literal, literal => boolean): #a #op #b == #expectedResult"() {
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
        a         | op   | b          | expectedResult
        20        | '==' | 20         | true
        20        | '==' | 3.0        | false
        75        | '==' | "'76'"     | false
        75        | '==' | "'76.0'"   | false
        7.0       | '==' | 3          | false
        7.0       | '==' | 2.0        | false
        41.0      | '==' | "'38'"     | false
        41.0      | '==' | "'381.0'"  | false
        true      | '==' | '!false'   | true
        true      | '==' | true       | true
        true      | '==' | "'true'"   | true
        true      | '==' | "'false'"  | false
        true      | '==' | "!'false'" | true
        "'foo'"   | '==' | "'foo'"    | true
        "'bar'"   | '==' | "'foo'"    | false
        "'baz'"   | '==' | null       | false
        "'1'"     | '==' | 10         | false
        "'1'"     | '==' | 1.0        | true
        "'2.0'"   | '==' | 2          | true
        "'2.0'"   | '==' | 2.0        | true
        "'true'"  | '==' | true       | true
        "'false'" | '==' | true       | false
        null      | '==' | null       | true
        null      | '==' | "'blah'"   | false
        20        | '!=' | 20         | false
        20        | '!=' | 3.0        | true
        75        | '!=' | "'76'"     | true
        75        | '!=' | "'76.0'"   | true
        7.0       | '!=' | 3          | true
        7.0       | '!=' | 2.0        | true
        41.0      | '!=' | "'38'"     | true
        41.0      | '!=' | "'381.0'"  | true
        true      | '!=' | '!true'    | true
        true      | '!=' | true       | false
        true      | '!=' | "'true'"   | false
        true      | '!=' | "'false'"  | true
        true      | '!=' | "!'true'"  | true
        "'foo'"   | '!=' | "'foo'"    | false
        "'bar'"   | '!=' | "'foo'"    | true
        "'baz'"   | '!=' | null       | true
        "'1'"     | '!=' | 10         | true
        "'1'"     | '!=' | 1.0        | false
        "'2.0'"   | '!=' | 2          | false
        "'2.0'"   | '!=' | 2.0        | false
        "'true'"  | '!=' | true       | false
        "'false'" | '!=' | true       | true
        null      | '!=' | null       | false
        null      | '!=' | "'blah'"   | true
    }

    def "Simple equality (literal, #bType => Boolean): #a #op #b == #expectedResult"() {
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
        'int'      | 7       | '=='  | 2             | false
        'double'   | 7       | '=='  | 2.0           | false
        'int'      | 7.0     | '=='  | 2             | false
        'Integer'  | null    | '=='  | 2             | false
        'boolean'  | 'true'  | '==!' | 'true'        | false
        'Boolean'  | 'false' | '==!' | 'true'        | true
        'String'   | "'foo'" | '=='  | '"bar"'       | false
        'String'   | "'foo'" | '=='  | '"foo"'       | true
        'Object'   | 'null'  | '=='  | 'null'        | true
        'String'   | null    | '=='  | '"bar"'       | false
        'String'   | true    | '=='  | '"baz"'       | false
        'Numbers'  | "'TWO'" | '=='  | 'Numbers.TWO' | true
        'Numbers'  | 2       | '=='  | 'Numbers.TWO' | true
        'Numbers'  | 2.0     | '=='  | 'Numbers.TWO' | true
        'Numbers'  | true    | '=='  | 'Numbers.TWO' | false
        'Double'   | 7       | '!='  | 2.0           | true
        'int'      | -3      | '!=-' | 3             | false
        'String'   | "'foo'" | '!='  | '"bar"'       | true
        'String'   | "'foo'" | '!='  | '"foo"'       | false
        'Object'   | 'null'  | '!='  | 'null'        | false
    }

    def "Simple equality (#aType, literal => boolean): #a #op #b == #expectedResult"() {
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
        aType     | a       | op   | b       | expectedResult
        'int'     | 7       | '==' | 2       | false
        'int'     | 7       | '==' | "'77'"  | false
        'String'  | '"bar"' | '==' | "'foo'" | false
        'Integer' | null    | '==' | null    | true
        'int'     | 7       | '!=' | 2       | true
        'int'     | 7       | '!=' | "'77'"  | true
    }

    def "Simple equality (#aType, #bType => Boolean): #a #op #b == #expectedResult"() {
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
        aType      | bType      | a             | op   | b             | expectedResult
        'int'      | 'double'   | 7             | '==' | 2             | false
        'String'   | 'double'   | '"65.0"'      | '==' | 3             | false
        'Numbers'  | 'Numbers'  | 'Numbers.ONE' | '==' | 'Numbers.TWO' | false
        'Numbers'  | 'Numbers'  | 'Numbers.ONE' | '==' | 'Numbers.ONE' | true
        'String'   | 'String'   | '"foo"'       | '==' | '"bar"'       | false
        'String'   | 'String'   | '"foo"'       | '==' | '"foo"'       | true
        'Integer'  | 'boolean'  | 1             | '==' | false         | true
        'double[]' | 'double[]' | '{1.0}'       | '==' | '{1.0}'       | false
        'int'      | 'double'   | 7             | '!=' | 2             | true
        'String'   | 'double'   | '"65.0"'      | '!=' | 3             | true
        'Numbers'  | 'Numbers'  | 'Numbers.ONE' | '!=' | 'Numbers.TWO' | true
        'Numbers'  | 'Numbers'  | 'Numbers.ONE' | '!=' | 'Numbers.ONE' | false
        'String'   | 'Object'   | '"bar"'       | '!=' | '"bar"'       | false
        'Object'   | 'String'   | '"bar"'       | '!=' | '"foo"'       | true
    }

    def "Simple equality NC (literal, literal => boolean): #a #op #b fails with '#expectedResult'"() {
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
        a           | op   | b        | expectedResult
        1           | '==' | null     | EqualityContinuation.TYPE_ERROR
        1           | '==' | true     | EqualityContinuation.TYPE_ERROR
        1           | '==' | "'foo'"  | EqualityContinuation.TYPE_ERROR
        2.0         | '==' | null     | EqualityContinuation.TYPE_ERROR
        2.0         | '==' | false    | EqualityContinuation.TYPE_ERROR
        2.0         | '==' | "'baz'"  | EqualityContinuation.TYPE_ERROR
        true        | '==' | 1        | EqualityContinuation.TYPE_ERROR
        false       | '==' | 1.0      | EqualityContinuation.TYPE_ERROR
        false       | '==' | null     | EqualityContinuation.TYPE_ERROR
        "'foo'"     | '==' | 1        | EqualityContinuation.TYPE_ERROR
        "'bar'"     | '==' | 35.0     | EqualityContinuation.TYPE_ERROR
        null        | '==' | 1        | EqualityContinuation.TYPE_ERROR
        null        | '==' | 1.0      | EqualityContinuation.TYPE_ERROR
        null        | '==' | true     | EqualityContinuation.TYPE_ERROR
    }

    def "Simple equality NC(#aType, #bType => boolean): #a #op #b fails with '#expectedResult'"() {
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
        aType        | bType        | a     | op   | b             | expectedResult
        String.name  | Numbers.name | '"a"' | '==' | 'Numbers.ONE' | String.format(EqualityContinuation.TYPE_ERROR_FORMAT, aType, bType)
    }
}
