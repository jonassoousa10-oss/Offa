package com.nexora.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the client's private key encrypted under a non-exportable Android Keystore key. */
object EncryptedProfileStore {
    private const val ALIAS = "nexora_profile_key_v1"
    private const val FILE_NAME = "nexora_profile.bin"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun exists(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists()

    fun save(context: Context, bytes: ByteArray) {
        require(bytes.size <= 65536) { "Arquivo de perfil muito grande." }
        // Validate before storing. Never print this Config: it contains a private key.
        Config.parse(ByteArrayInputStream(bytes))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(bytes)
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.outputStream().use { stream ->
            stream.write(iv.size)
            stream.write(iv)
            stream.write(ciphertext)
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Não foi possível salvar o perfil com segurança.")
        }
    }

    fun load(context: Context): Config {
        val bytes = File(context.filesDir, FILE_NAME).readBytes()
        check(bytes.size > 13) { "Perfil criptografado inválido." }
        val ivLen = bytes[0].toInt() and 0xff
        check(ivLen == 12 && bytes.size > 1 + ivLen) { "Dados criptografados inválidos." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        val clear = cipher.doFinal(bytes, 13, bytes.size - 13)
        return try {
            Config.parse(ByteArrayInputStream(clear))
        } finally {
            clear.fill(0)
        }
    }

    fun remove(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        context.getSharedPreferences("nexora", Context.MODE_PRIVATE).edit().remove("profileName").apply()
    }
}
