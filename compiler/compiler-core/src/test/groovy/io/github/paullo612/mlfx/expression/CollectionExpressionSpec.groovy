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

class CollectionExpressionSpec extends ExpressionSpec {

    def "Simple collection operation (literal, literal => String): #a[#b] == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a[$b]")
    abstract String compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        a        | b     | expectedResult
        "'bar'"  | 1     | 'a'
        "'bar'"  | 0.0   | 'b'
        "'bar'"  | "'2'" | 'r'
        "'bar'"  | "'2'" | 'r'
        1        | 0     | '1'
        2.0      | 1     | '.'
        true     | 3     | 'e'
    }

    def "Simple collection operation (literal, #bType => String): #a[#b] == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $bType b = $b;
    
    @Expression.Value("$a[b]")
    abstract String compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        bType    | a       | b     | expectedResult
        'int'    | "'bar'" | 2     | 'r'
        'double' | "'bar'" | 2.53  | 'r'
        'String' | "'bar'" | '"0"' | 'b'
        'int'    | 143     | 2     | '3'
        'double' | 7.156   | 2.53  | '1'
        'String' | false   | '"0"' | 'f'
    }

    def "Simple collection operation (#aType, literal => #ret): #a[#b] == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import java.util.*;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    
    @Expression.Value("a[$b]")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType                | ret        | a                                   | b     | expectedResult
        'String'             | 'String'   | '"bar"'                             | 2     | 'r'
        'String'             | 'String'   | '"bar"'                             | 1.48  | 'a'
        'String'             | 'String'   | '"bar"'                             | "'0'" | 'b'
        'int'                | 'String'   | 143                                 | 2     | '3'
        'double'             | 'String'   | 7.156                               | 2.53  | '1'
        'boolean'            | 'String'   | false                               | "'0'" | 'f'
        'List<Integer>'      | 'Integer'  | 'List.of(1, 2, 3)'                  | 1     | 2
        'List<Integer>'      | 'Integer'  | 'List.of(1, 2, 3)'                  | 0.75  | 1
        'List<Integer>'      | 'Integer'  | 'List.of(1, 2, 3)'                  | "'2'" | 3
        'List'               | 'Integer'  | 'List.of(1, 2, 3)'                  | 0     | 1
        'double[]'           | 'double'   | '{4., 5., 6.}'                      | 1     | 5.0
        'Double[]'           | 'double'   | '{4., 5., 6.}'                      | 2.41  | 6.0
        'double[][]'         | 'double[]' | '{{4.}, {5.}, {6.}}'                | "'0'" | new double[] {4.0}
        'Double[][]'         | 'Double[]' | '{{4.}, {5.}, {6.}}'                | 1     | new Double[] {5.0}
        'Map<String, Long>'  | 'long'     | 'Map.of("a", 1L, "b", 3L)'          | "'b'" | 3
        'Map<Byte, Long>'    | 'long'     | 'Map.of((byte)3, 1L, (byte)7, 3L)'  | "'7'" | 3
        'Map<Byte, Long>'    | 'long'     | 'Map.of((byte)3, 1L, (byte)7, 3L)'  | 3     | 1
        'Map<Byte, Long>'    | 'long'     | 'Map.of((byte)3, 1L, (byte)7, 3L)'  | 3.5   | 1
        'Map<Byte, Long>'    | 'long'     | 'new HashMap<>(){{put(null, 3L);}}' | null  | 3
        'Map<Boolean, Long>' | 'long'     | 'Map.of(true, 1L, false, 25L)'      | false | 25
        'Map'                | 'long'     | 'Map.of("a", 1L, "b", 3L)'          | "'a'" | 1
    }

    def "Simple collection operation (#aType, #bType => #ret): #a[#b] == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import java.util.*;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    final $bType b = $b;
    
    @Expression.Value("a[b]")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType                | bType    | ret        | a                        | b     | expectedResult
        'String'             | 'String' | 'String'   | '"bar"'                  | '"2"' | 'r'
        'String'             | int      | 'String'   | '"bar"'                  | 2     | 'r'
        'List<Integer>'      | 'String' | 'Integer'  | 'List.of(1, 2, 3)'       | '"1"' | 2
        'List<Integer>'      | int      | 'Integer'  | 'List.of(1, 2, 3)'       | 1     | 2
        'double[]'           | 'String' | 'double'   | '{4., 5., 6.}'           | '"1"' | 5.0
        'double[]'           | int      | 'double'   | '{4., 5., 6.}'           | 1     | 5.0
        'Map<Long, Long>'    | 'String' | 'long'     | 'Map.of(2L, 1L, 4L, 3L)' | '"4"' | 3
        'Map<Long, Long>'    | 'Long'   | 'long'     | 'Map.of(2L, 1L, 4L, 3L)' | '4L'  | 3
    }

    def "Simple collection operation NC (literal, literal => String): #a[#b] fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a[$b]")
    abstract String compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        a       | b                       | expectedResult
        null    | 1                       | CollectionContinuation.NULL_TYPE_ERROR
        "'foo'" | -1                      | String.format(CollectionContinuation.ILLEGAL_INDEX_ERROR_FORMAT, b)
        "'foo'" | Integer.MAX_VALUE + 1L  | String.format(CollectionContinuation.ILLEGAL_INDEX_ERROR_FORMAT, b)
        "'foo'" | -2.0                    | String.format(CollectionContinuation.ILLEGAL_INDEX_ERROR_FORMAT, b as long)
        "'foo'" | Integer.MAX_VALUE + 1D  | String.format(CollectionContinuation.ILLEGAL_INDEX_ERROR_FORMAT, b as long)
        "'foo'" | Integer.MAX_VALUE + 1.5 | String.format(CollectionContinuation.ILLEGAL_INDEX_ERROR_FORMAT, b as long)
        "'foo'" | null                    | CollectionContinuation.INDEX_NULL_TYPE_ERROR
        "'foo'" | true                    | CollectionContinuation.INDEX_BOOLEAN_TYPE_ERROR
        "'foo'" | 7                       | CollectionContinuation.OUT_OF_BOUNDS_ERROR
        "'foo'" | "'bar'"                 | String.format(CollectionContinuation.STRING_INDEX_ERROR_FORMAT, b.replace("'", ''))
    }

    def "Simple collection operation NC (#aType, literal => String): #a[#b] fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import java.util.*;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final $aType a = $a;
        
    @Expression.Value("a[$b]")
    abstract String compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        aType           | a                  | b     | expectedResult
        'String'        | '"foo"'            | null  | CollectionContinuation.INDEX_NULL_TYPE_ERROR
        'List<Integer>' | 'List.of(1, 2, 3)' | false | CollectionContinuation.INDEX_BOOLEAN_TYPE_ERROR
    }
}
