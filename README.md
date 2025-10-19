# Kompiler

Simple kompiler framework for building compilers and interpreters. 
Example project include ONP (Reverse Polish Notation) interpreter.

```kotlin
    val onpKompiler = onpKompiler()

    @Test
    fun onpTest() {
        assertEquals("12 varA b c * d e / + * plus", onpKompiler.compile("12 plus arA * (b * c + d / e)"))
    }
```