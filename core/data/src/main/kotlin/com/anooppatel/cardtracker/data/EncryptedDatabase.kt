package com.anooppatel.cardtracker.data

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/** File name of the database inside the app's private storage. */
const val DATABASE_NAME = "duenextbill.db"

/**
 * Opens the app's database, encrypted with SQLCipher. The key comes from [DatabaseKeyStore].
 *
 * The wrapped key is kept in noBackupFilesDir: a backup restored onto another phone wouldn't have
 * this phone's Keystore key, so it could never unwrap it anyway.
 */
fun openEncryptedDatabase(
    context: Context,
    name: String = DATABASE_NAME,
    keyWrapper: KeyWrapper = KeystoreKeyWrapper(),
): AppDatabase {
    System.loadLibrary("sqlcipher")

    val keyStore = DatabaseKeyStore(File(context.noBackupFilesDir, "$name.key"), keyWrapper)
    return Room.databaseBuilder(context, AppDatabase::class.java, name)
        .openHelperFactory(SupportOpenHelperFactory(keyStore.getOrCreate()))
        .build()
}