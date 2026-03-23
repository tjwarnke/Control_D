package com.example.controld.userDatabase

import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val ALGORITHM = "PBKDF2WithHmacSHA512"
private const val ITERATIONS = 120_000
private const val KEY_LENGTH = 256

/*
For simplicity, the secret value is stored as a constant,
but in a real-life scenario, we’d rather pass it as an
environment variable
*/
private const val SECRET = "ThisIsASecretOOPS"


fun generateRandomSalt(): String{
    val random = SecureRandom()
    val salt = ByteArray(16)
    random.nextBytes(salt)
    return salt.toString()
}



private fun ByteArray.toHex(): String = joinToString(separator = "") {
    eachByte -> "%02x".format(eachByte)
}

fun generateHash(password: String, salt: String):String{
    val combinedSalt = "$salt$SECRET".toByteArray()

    val factory: SecretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM)
    val spec = PBEKeySpec(password.toCharArray(), combinedSalt, ITERATIONS, KEY_LENGTH)
    val key: SecretKey = factory.generateSecret(spec)
    val hash: ByteArray = key.encoded

    return hash.toHex()
}

fun main() {
    var testPassword = "Password1234"
    var salt = generateRandomSalt()
    val res = generateHash(testPassword, salt)
    println(res)
}