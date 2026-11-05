package com.anooppatel.cardtracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Runs on your phone: real SQLCipher and the real Android Keystore.
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "encrypted-test.db"
    private val keyFile get() = File(context.noBackupFilesDir, "$name.key")
    private val wrapper = KeystoreKeyWrapper(alias = "duenextbill-test-key")

    private val sbi = CardEntity(bank = "SBI", last4 = "1111", nickname = "SBI", statementDay = 8, dueOffsetDays = 20)

    @Before
    @After
    fun cleanUp() {
        context.deleteDatabase(name)
        keyFile.delete()
    }

    @Test
    fun keystoreWrapperRoundTrips() {
        val secret = ByteArray(32) { it.toByte() }

        val wrapped = wrapper.wrap(secret)

        wrapped.toList() shouldNotBe secret.toList()
        wrapper.unwrap(wrapped).toList() shouldBe secret.toList()
        // A fresh random IV each time, so wrapping the same secret twice looks different
        wrapper.wrap(secret).toList() shouldNotBe wrapped.toList()
    }

    @Test
    fun dataSurvivesClosingAndReopening() = runBlocking<Unit> {
        openEncryptedDatabase(context, name, wrapper).apply {
            cardDao().insert(sbi)
            close()
        }

        val reopened = openEncryptedDatabase(context, name, wrapper)
        reopened.cardDao().byId(1)!!.last4 shouldBe "1111"
        reopened.close()
    }

    @Test
    fun theFileOnDiskIsNotPlainSqlite() = runBlocking<Unit> {
        openEncryptedDatabase(context, name, wrapper).apply {
            cardDao().insert(sbi)
            close()
        }

        // Every normal SQLite file starts with these 16 bytes. An encrypted one looks like random noise.
        val header = context.getDatabasePath(name).readBytes().copyOf(16)
        header.toList() shouldNotBe "SQLite format 3\u0000".toByteArray().toList()
    }

    @Test
    fun withoutTheKeyTheDataCannotBeRead() = runBlocking<Unit> {
        openEncryptedDatabase(context, name, wrapper).apply {
            cardDao().insert(sbi)
            close()
        }

        keyFile.delete() // the next open makes a brand-new, wrong key

        val wrongKey = openEncryptedDatabase(context, name, wrapper)
        shouldThrowAny { wrongKey.cardDao().byId(1) }
        wrongKey.close()
    }
}
