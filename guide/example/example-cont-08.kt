// This file was automatically generated from Cont.kt by Knit tool. Do not edit.
package example.exampleCont08

import arrow.*
import arrow.core.*
import arrow.fx.coroutines.*
import kotlinx.coroutines.*
import io.kotest.matchers.collections.*
import io.kotest.assertions.*
import io.kotest.matchers.*
import io.kotest.matchers.types.*
import kotlin.coroutines.cancellation.CancellationException
import io.kotest.property.*
import io.kotest.property.arbitrary.*
import arrow.core.test.generators.*

private val default = "failed"
suspend fun test() = checkAll(Arb.option(Arb.int())) { option ->
  cont<String, Int> {
    val x: Int = option.bind { default }
    x
  }.fold({ default }, ::identity) shouldBe option.getOrElse { default }
}
