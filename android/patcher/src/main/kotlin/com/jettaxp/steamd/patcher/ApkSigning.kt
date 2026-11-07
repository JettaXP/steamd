package com.jettaxp.steamd.patcher

import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

class SigningKey(val privateKey: PrivateKey, val certificate: X509Certificate) {
    fun save(file: File, password: CharArray) {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, privateKey, password, arrayOf(certificate))
        file.outputStream().use { ks.store(it, password) }
    }

    companion object {
        private const val ALIAS = "steamd"

        fun generate(commonName: String = "steamd"): SigningKey {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val name = X500Name("CN=$commonName")
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger(64, SecureRandom()), Date(now - 86_400_000L),
                Date(now + 30L * 365 * 86_400_000L), name, kp.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            return SigningKey(kp.private, cert)
        }

        fun load(file: File, password: CharArray): SigningKey {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, password) }
            return SigningKey(ks.getKey(ALIAS, password) as PrivateKey, ks.getCertificate(ALIAS) as X509Certificate)
        }

        fun loadOrCreate(file: File, password: CharArray): SigningKey =
            if (file.exists()) load(file, password) else generate().also { file.parentFile?.mkdirs(); it.save(file, password) }
    }
}

object ApkSigning {
    fun sign(input: File, output: File, key: SigningKey, minSdk: Int? = null) {
        val config = ApkSigner.SignerConfig.Builder("steamd", key.privateKey, listOf(key.certificate)).build()
        ApkSigner.Builder(listOf(config))
            .apply { if (minSdk != null) setMinSdkVersion(minSdk) }
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setCreatedBy("steamd")
            .build()
            .sign()
    }
}
