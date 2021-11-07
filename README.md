# Cont<R, A>

`Cont<R, A>` represents a function of `suspend () -> A` that can fail with `R` (and `Throwable`), so it's defined
by `suspend fun <B> fold(f: suspend (R) -> B, g: suspend (A) -> B): B`.

So to construct a `Cont<R, A>` we simply call the `cont<R, A> { }` DSL, which exposes a rich syntax through the lambda
receiver `suspend ContEffect<R>.() -> A`.

What is interesting about the `Cont<R, A>` type is that it doesn't rely on any wrappers such as `Either`, `Ior`
or `Validated`. Instead `Cont<R, A>` represents a suspend function, and only when we call `fold` it will actually create
a `Continuation` and run the computation.

This makes `Cont<R, A>` a very efficient generic runtime.

## Writing a program with Cont<R, A>

Let's write a small program to read a file from disk, and instead of having the program work exception based we want to
turn it into a polymorphic type-safe program.

We'll start by defining a small function that accepts a `String`, and does some simply validation to check that the path
is not empty. If the path is empty, we want to program to result in `EmptyPath`. So we're immediately going to see how
we can raise an error of any arbitrary type `R` by using the function `shift`. The name `shift` comes shifting (or
changing, especially unexpectedly), away from the computation and finishing the `Continuation` with `R`.

```kotlin
object EmptyPath

fun readFile(path: String): Cont<EmptyPath, Unit> = cont {
  if (path.isNotEmpty()) shift(EmptyPath) else Unit
}
```

Here we see how we can define a `Cont<R, A>` which has `EmptyPath` for the shift type `R`, and `Unit` for the success
type `A`.

Patterns like validating a `Boolean` is very common, and the `Cont` DSL offers utility functions like `kotlin.require`
and `kotlin.requireNotNull`. They're named `ensure` and `ensureNotNull` to avoid conflicts with the `kotlin` namespace.
So let's rewrite the function from above to use the DSL instead.

```kotlin
fun readFile(path: String?): Cont<EmptyPath, Unit> = cont {
  ensure(path.isNotEmpty()) { EmptyPath }
}
```

Now that we have the path, we can read the from the `File` and return it as a domain model `Content`
We also want to take a look at what exceptions reading from a file might occur `FileNotFoundException` & `SecurityError`
, so lets make some domain errors for those too. Grouping them as a sealed interface is useful since that way we can
resolve *all* errors in a type safe manner.

```kotlin
@JvmInline
value class Content(val body: List<String>)

sealed interface FileError
@JvmInline
value class SecurityError(val msg: String?) : FileError()
@JvmInline
value class FileNotFound(val path: String) : FileError()
object EmptyPath : FileError()
```

We can finish our function, but we need to refactor our return value from `Unit` to `Content` and our error type
from `EmptyPath` to `FileError`.

```kotlin
fun readFile(path: String): Cont<FileError, Content> = cont {
  ensure(path.isNotEmpty()) { EmptyPath }
  try {
    val lines = File(path).readLines()
    Content(lines)
  } catch (e: FileNotFoundException) {
    shift(FileNotFound(path))
  } catch (e: SecurityException) {
    shift(SecurityError(e.message))
  }
}
```

The `readFile` function defines a `suspend fun` that will return:

- the `Content` of a given `path`
- a `FileError`
- An unexpected fatal error (OutOfMemoryException)

Since these are the properties of our `Cont` function, we can turn it into a value.

```kotlin
suspend fun main() {
  readFile("").toEither() //Either.Left(EmptyPath@3dd3bcd)
  readFile("not-found").toValidated() //Validated.Invalid(FileNotFound(path=not-found)) 
  readFile("gradle.properties").toIor() //Ior.Right(Content(body=[kotlin.code.style=official]))
  readFile("not-found").toOption { None } //Option.None
  readFile("nullable").fold({ _: FileError -> null }, { it }) //null
}
```

The functions above our available out of the box, but it's easy to define your own extension functions in terms
of `fold`. Implementing the `toEither()` operator is as simple as:

```kotlin
suspend fun <R, A> Cont<R, A>.toEither(): Either<R, A> =
  fold({ Either.Left(it) }) { Either.Right(it) }

suspend fun <A> Cont<None, A>.toOption(): Option<A> =
  fold(::identity) { Some(it) }
```

Adding your own syntax to `ContEffect<R>` is tricky atm, but will be easy once "Multiple Receivers" become available.

```kotlin
context(ContEffect<R>)
suspend fun <R, A> Either<R, A>.bind(): A =
  when (this) {
    is Either.Left -> shift(value)
    is Either.Right -> value
  }

context(ContEffect<None>)
fun <A> Option<A>.bind(): A =
  fold({ shift(it) }, ::identity)
```

## Handling errors

Handling errors of type `R` is the same as handling errors for any other data type in Arrow.
`Cont<R, A>` offers `handleError`, `handleErrorWith`, `redeem`, `redeemWith` and `attempt`.

As you can see in the examples below it is possible to resolve errors of `R` or `Throwable` in `Cont<R, A>` in a generic manner.
There is no need to run `Cont<R, A>` into `Either<R, A>` before you can access `R`,
you can simply call the same functions on `Cont<R, A>` as you would on `Either<R, A>` directly.

```kotlin
val failed: Cont<String, Int> =
  cont { shift("failed") }

val resolved: Cont<Nothing, Int> =
  failed.handleError { it.length }

val newError: Cont<ByteArray, Int> =
  failed.handleErrorWith { str ->
    cont { shift(str.toByteArray()) }
  }

val redeemed: Cont<Nothing, String> =
  failed.redeem(::identity, Int::toString)

val captured: Cont<String, Result<Int>> = cont<String, Int> {
  throw RuntimeException("Boom")
}.attempt()
```

**NOTE**:
cancellation of Coroutines in Kotlin works exception based, so one can also recover from errors of type `R`
using `try/catch`. It's however not recommended to capture `Throwable`, or Arrow's `ControlThrowable`. More can be read
in ContAndStructuredConcurrency.md
