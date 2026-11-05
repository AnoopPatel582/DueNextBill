package com.anooppatel.cardtracker.data

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// The real Keystore only exists on a phone (see EncryptedDatabaseTest in androidTest).
// Here a stand-in wrapper checks the "make once, then reuse" logic on the laptop.
class DatabaseKeyStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Not real encryption: flips bits so we can see the stored bytes differ from the key. */
    private val fakeWrapper = object : KeyWrapper {
        override fun wrap(secret: ByteArray) = ByteArray(secret.size) { (secret[it].toInt() xor 0x5A).toByte() }

        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
    }

    private fun keyFile() = File(folder.root, "keys/test.db.key")

    @Test
    fun `makes a 256-bit key`() {
        DatabaseKeyStore(keyFile(), fakeWrapper).getOrCreate().size shouldBe 32
    }

    @Test
    fun `gives the same key every time, even after the app restarts`() {
        val first = DatabaseKeyStore(keyFile(), fakeWrapper).getOrCreate()

        DatabaseKeyStore(keyFile(), fakeWrapper).getOrCreate().toList() shouldBe first.toList()
    }

    @Test
    fun `stores only the wrapped key, never the key itself`() {
        val key = DatabaseKeyStore(keyFile(), fakeWrapper).getOrCreate()

        keyFile().readBytes().toList() shouldNotBe key.toList()
        keyFile().readBytes().toList() shouldBe fakeWrapper.wrap(key).toList()
    }

    @Test
    fun `different installs get different keys`() {
        val one = DatabaseKeyStore(File(folder.root, "one.key"), fakeWrapper).getOrCreate()
        val two = DatabaseKeyStore(File(folder.root, "two.key"), fakeWrapper).getOrCreate()

        one.toList() shouldNotBe two.toList()
    }

    @Test
    fun `leaves no temporary file behind`() {
        DatabaseKeyStore(keyFile(), fakeWrapper).getOrCreate()

        keyFile().parentFile!!.list()!!.toList() shouldBe listOf("test.db.key")
    }
}