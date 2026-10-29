package com.anooppatel.cardtracker.engine

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// Smoke test: proves JUnit 5, Kotest assertions and property testing are wired up.
// Delete it once the engine has real tests.
class TestSetupTest {

    @Test
    fun `kotest assertions work`() {
        (2 + 2) shouldBe 4
    }

    @Test
    fun `property tests work`() = runTest {
        checkAll(Arb.int(-1000..1000), Arb.int(-1000..1000)) { a, b ->
            (a + b) shouldBe (b + a)
        }
    }
}
