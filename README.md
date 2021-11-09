# Module Cont<R, A>

<!--- TEST_NAME ReadmeTest -->
<!--- TOC -->

* [Writing a program with Cont<R, A>](#writing-a-program-with-cont<r-a>)
* [Handling errors](#handling-errors)
* [Structured Concurrency](#structured-concurrency)
  * [Arrow Fx Coroutines](#arrow-fx-coroutines)
    * [parZip](#parzip)
    * [parTraverse](#partraverse)
    * [raceN](#racen)
    * [bracketCase / Resource](#bracketcase--resource)
  * [KotlinX](#kotlinx)
    * [withContext](#withcontext)
    * [async](#async)
    * [launch](#launch)

<!--- END -->

`Cont<R, A>` represents a function of `suspend () -> A` that can fail with `R` (and `Throwable`), so it's defined
by `suspend fun <B> fold(f: suspend (R) -> B, g: suspend (A) -> B): B`.

So to construct a `Cont<R, A>` we simply call the `cont<R, A> { }` DSL, which exposes a rich syntax through the lambda
receiver `suspend ContEffect<R>.() -> A`.

What is interesting about the `Cont<R, A>` type is that it doesn't rely on any wrappers such as `Either`, `Ior`
or `Validated`. Instead `Cont<R, A>` represents a suspend function, and only when we call `fold` it will actually create
a `Continuation` and runs the computation (without intercepting).
This makes `Cont<R, A>` a very efficient generic runtime.

## Writing a program with Cont<R, A>

Let's write a small program to read a file from disk, and instead of having the program work exception based we want to
turn it into a polymorphic type-safe program.

We'll start by defining a small function that accepts a `String`, and does some simply validation to check that the path
is not empty. If the path is empty, we want to program to result in `EmptyPath`. So we're immediately going to see how
we can raise an error of any arbitrary type `R` by using the function `shift`. The name `shift` comes shifting (or
changing, especially unexpectedly), away from the computation and finishing the `Continuation` with `R`.

<!--- INCLUDE
import arrow.Cont
import arrow.cont
-->
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
fun readFile2(path: String?): Cont<EmptyPath, Unit> = cont {
  ensure(!path.isNullOrBlank()) { EmptyPath }
}
```

> You can get the full code [here](guide/example/example-readme-01.kt).

Now that we have the path, we can read from the `File` and return it as a domain model `Content`.
We also want to take a look at what exceptions reading from a file might occur `FileNotFoundException` & `SecurityError`,
so lets make some domain errors for those too. Grouping them as a sealed interface is useful since that way we can resolve *all* errors in a type safe manner.

<!--- INCLUDE
import arrow.Cont
import arrow.cont
import arrow.ensureNotNull
import arrow.core.None
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
-->
```kotlin
@JvmInline
value class Content(val body: List<String>)

sealed interface FileError
@JvmInline value class SecurityError(val msg: String?) : FileError
@JvmInline value class FileNotFound(val path: String) : FileError
object EmptyPath : FileError {
  override fun toString() = "EmptyPath"
}
```

We can finish our function, but we need to refactor our return value from `Unit` to `Content` and our error type from `EmptyPath` to `FileError`.

```kotlin
fun readFile(path: String?): Cont<FileError, Content> = cont {
  ensureNotNull(path) { EmptyPath }
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
- An unexpected fatal error (`OutOfMemoryException`)

Since these are the properties of our `Cont` function, we can turn it into a value.

```kotlin
fun main() = runBlocking<Unit> {
  readFile("").toEither().also(::println)
  readFile("not-found").toValidated().also(::println) 
  readFile("gradle.properties").toIor().also(::println)
  readFile("not-found").toOption { None }.also(::println)
  readFile("nullable").fold({ _: FileError -> null }, { it }).also(::println)
}
```

> You can get the full code [here](guide/example/example-readme-02.kt).

```text
Either.Left(EmptyPath)
Validated.Invalid(FileNotFound(path=not-found))
Ior.Left(FileNotFound(path=gradle.properties))
Option.None
null
```
<!--- TEST -->

The functions above our available out of the box, but it's easy to define your own extension functions in terms
of `fold`. Implementing the `toEither()` operator is as simple as:

<!--- INCLUDE
import arrow.Cont
import arrow.core.identity
import arrow.core.Either
import arrow.core.Option
import arrow.core.None
import arrow.core.Some
-->
```kotlin
suspend fun <R, A> Cont<R, A>.toEither(): Either<R, A> =
  fold({ Either.Left(it) }) { Either.Right(it) }

suspend fun <A> Cont<None, A>.toOption(): Option<A> =
  fold(::identity) { Some(it) }
```

> You can get the full code [here](guide/example/example-readme-03.kt).

Adding your own syntax to `ContEffect<R>` is tricky atm, but will be easy once "Multiple Receivers" become available.

```
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

<!--- INCLUDE
import arrow.Cont
import arrow.cont
import arrow.core.identity
import kotlinx.coroutines.runBlocking
-->
```kotlin
val failed: Cont<String, Int> =
  cont { shift("failed") }

val resolved: Cont<Nothing, Int> =
  failed.handleError { it.length }

val newError: Cont<List<Char>, Int> =
  failed.handleErrorWith { str ->
    cont { shift(str.reversed().toList()) }
  }

val redeemed: Cont<Nothing, Int> =
  failed.redeem({ str -> str.length }, ::identity)

val captured: Cont<String, Result<Int>> = cont<String, Int> {
  throw RuntimeException("Boom")
}.attempt()

fun main() = runBlocking<Unit> {
  println(failed.toEither())
  println(resolved.toEither())
  println(newError.toEither())
  println(redeemed.toEither())
  println(captured.toEither())
}
```

> You can get the full code [here](guide/example/example-readme-04.kt).

```text
Either.Left(failed)
Either.Right(6)
Either.Left([d, e, l, i, a, f])
Either.Right(6)
Either.Right(Failure(java.lang.RuntimeException: Boom))
```
<!--- TEST -->

Note:
 Handling errors can also be done with `try/catch` but this is **not recommended**, it uses `CancellationException` which is used to cancel `Coroutine`s and is advised not to capture in Kotlin. 
 The `CancellationException` from `Cont` is `ShiftCancellationException`, this type is public so you can distinct the exceptions if necessary.

## Structured Concurrency

`Cont<R, A>` relies on `kotlin.cancellation.CancellationException` to `shift` error values of type `R` inside the `Continuation` since it effectively cancels/short-circuits it.
For this reason `shift` adheres to the same rules as [`Structured Concurrency`](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)

Let's overview below how `shift` behaves with the different concurrency builders from Arrow Fx & KotlinX Coroutines.

### Arrow Fx Coroutines
All operators in Arrow Fx Coroutines run in place, so they have no way of leaking `shift`.
It's there always safe to compose `cont` with any Arrow Fx combinator. Let's see some small examples below.

#### parZip
<!--- INCLUDE
import arrow.cont
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.delay
-->
```kotlin
suspend fun parZip(): Unit = cont<String, Int> {
  parZip({
   delay(1_000_000) // Cancelled by shift 
  }, { shift<Int>("error") }) { _, int -> int }
}.fold(::println, ::println) // "error"
```

> You can get the full code [here](guide/example/example-readme-05.kt).

#### parTraverse
<!--- INCLUDE
import arrow.cont
import arrow.fx.coroutines.onCancel
import arrow.fx.coroutines.parTraverse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() = runBlocking {
  cont<String, List<Unit>> {
    (1..5).parTraverse { index ->
      if (index == 5) shift("error")
      else onCancel({ delay(1_000_000) }) { println("I got cancelled") }
    }
  }.fold(::println, ::println)
}
```
`parTraverse` will launch 5 tasks, for every element in `1..5`.
The last task to get scheduled will `shift` with "error", and it will cancel the other launched tasks before returning.
```text
I got cancelled
I got cancelled
I got cancelled
I got cancelled
error
```
> You can get the full code [here](guide/example/example-readme-06.kt).
<!--- TEST -->

#### raceN
<!--- INCLUDE
import arrow.cont
import arrow.core.merge
import arrow.fx.coroutines.onCancel
import arrow.fx.coroutines.raceN
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
-->
```kotlin
fun main() = runBlocking {
  cont<String, Int> {
    raceN({
      onCancel({ delay(1_000_000) }) { println("I lost the race...") }
      5
    }) { shift<Int>("error") }
      .merge() // Flatten Either<Int, Int> result from race into Int
  }.fold(::println, ::println)
}
```
`raceN` races `n` suspend functions in parallel, and cancels all participating functions when a winner is found.
We can consider the function that `shift`s the winner of the race, except with a shifted value instead of a successful one.
So when a function in the race `shift`s, and thus short-circuiting the race, it will cancel all the participating functions. 
```text
I lost the race...
error
```
> You can get the full code [here](guide/example/example-readme-07.kt).
<!--- TEST -->

#### bracketCase / Resource
<!--- INCLUDE
import arrow.cont
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.bracketCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromAutoCloseable
import java.io.BufferedReader
import java.io.File
-->
```kotlin
suspend fun bracketCase() = cont<String, Int> {
  bracketCase(
   acquire = { File("gradle.properties").bufferedReader() },
   use = { reader -> 
    // some logic
    shift("file doesn't contain right content")
   },
   release = { reader, exitCase -> 
     reader.close()
     println(exitCase) // ExitCase.Cancelled(ShiftCancellationException("Shifted Continuation"))
   }
  )
}.fold(::println, ::println) // "file doesn't contain right content"

// Available from Arrow 1.1.x
fun <A> Resource<A>.releaseCase(releaseCase: (A, ExitCase) -> Unit): Resource<A> =
  flatMap { a -> Resource({ a }, releaseCase) }

fun bufferedReader(path: String): Resource<BufferedReader> =
  Resource.fromAutoCloseable {
    File(path).bufferedReader()
  }.releaseCase { _, exitCase -> println(exitCase) }

suspend fun resource() = cont<String, Int> {
  bufferedReader("gradle.properties").use { reader ->
  // some logic
  shift("file doesn't contain right content")
 } // ExitCase.Cancelled(ShiftCancellationException("Shifted Continuation")) printed from release
}
```
> You can get the full code [here](guide/example/example-readme-08.kt).

### KotlinX
#### withContext
It's always safe to call `shift` from `withContext` since it runs in place, so it has no way of leaking `shift`.
When `shift` is called from within `withContext` it will cancel all `Job`s running inside the `CoroutineScope` of `withContext`.

<!--- INCLUDE
import arrow.cont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import arrow.Cont
import arrow.ensureNotNull
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

@JvmInline
value class Content(val body: List<String>)

sealed interface FileError
@JvmInline value class SecurityError(val msg: String?) : FileError
@JvmInline value class FileNotFound(val path: String) : FileError
object EmptyPath : FileError {
  override fun toString() = "EmptyPath"
}

fun readFile(path: String?): Cont<FileError, Content> = cont {
  ensureNotNull(path) { EmptyPath }
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

fun <A: Job> A.onCancel(f: (CancellationException) -> Unit): A = also {
  invokeOnCompletion { error ->
    if (error is CancellationException) f(error) else Unit
  }
}
-->

```kotlin
fun main() = runBlocking<Unit> {
  cont<FileError, Int> {
    withContext(Dispatchers.IO) {
      launch { delay(1_000_000) }.onCancel { println("Cancelled due to shift: $it") }
      val sleeper = async { delay(1_000_000) }.onCancel { println("Cancelled due to shift: $it") }
      val content = readFile("failure").bind()
      sleeper.await()
      content.body.size
    }
  }.fold(::println, ::println)
}
```
> You can get the full code [here](guide/example/example-readme-09.kt).
```text
Cancelled due to shift: ShiftCancellationException(Shifted Continuation)
Cancelled due to shift: ShiftCancellationException(Shifted Continuation)
FileNotFound(path=failure)
```
<!--- TEST -->

#### async

When calling `shift` from `async` you should always call `await`, otherwise `shift` can leak out of its scope.

```kotlin
import arrow.cont
import arrow.core.identity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import io.kotest.matchers.collections.shouldBeIn

fun main() = runBlocking {
  coroutineScope {
    cont<Int, String> {
      val fa = async<String> { shift(1) }
      val fb = async<String> { shift(2) }
      fa.await() + fb.await()
    }.fold(::identity, ::identity) shouldBeIn listOf(1, 2)
  }
}
```
> You can get the full code [here](guide/example/example-readme-10.kt).
<!--- TEST lines.isEmpty() -->
#### launch


**NOTE**
Capturing `shift` into a lambda, and leaking it outside of `Cont` to be invoked outside will yield unexpected results.
Below we capture `shift` from inside the DSL, and then invoke it outside its context `ContEffect<String>`.

```kotlin
cont<String, suspend () -> Unit> {
 suspend { shift("error") }
}.fold({ }, { leakedShift -> leakedShift.invoke() })
```

The same violation is possible in all DSLs in Kotlin, including Structured Concurrency.

```kotlin
val leakedAsync = coroutineScope<suspend () -> Deferred<Unit>> {
  suspend {
    async {
      println("I am never going to run, until I get called invoked from outside")
    }
  }
}
leakedAsync.invoke().await()
```
