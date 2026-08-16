package com.example.kotlindp.patterns.spring.conditionalbeans

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

/**
 * # Conditional beans — Abstract Factory selected by configuration
 *
 * `@ConditionalOnProperty`, `@Profile` and `@ConditionalOnMissingBean` let the *container* pick the
 * implementation family, which is precisely what Abstract Factory (`creational/abstractfactory`)
 * does — without a hand-written factory or a `when` over an enum.
 *
 * The important difference from a runtime factory: this selection happens **once, at startup**. A
 * misconfigured value fails immediately with a clear message, and there is no per-call dispatch.
 */

// ---------------------------------------------------------------------------------------------
// The abstraction.
// ---------------------------------------------------------------------------------------------

interface StorageClient {
    val provider: String
    fun put(key: String, content: String): String
    fun url(key: String): String
}

interface StorageSigner {
    fun sign(key: String, ttlSeconds: Long): String
}

// ---------------------------------------------------------------------------------------------
// Family 1 — the default (local), active unless configured otherwise.
// ---------------------------------------------------------------------------------------------

class LocalStorageClient(private val root: String) : StorageClient {
    private val files = mutableMapOf<String, String>()

    override val provider = "local"
    override fun put(key: String, content: String): String {
        files[key] = content
        return "$root/$key"
    }

    override fun url(key: String) = "file://$root/$key"
}

class LocalStorageSigner : StorageSigner {
    override fun sign(key: String, ttlSeconds: Long) = "file://$key?ttl=$ttlSeconds"
}

// ---------------------------------------------------------------------------------------------
// Family 2 — S3.
// ---------------------------------------------------------------------------------------------

class S3StorageClient(private val bucket: String) : StorageClient {
    override val provider = "s3"
    override fun put(key: String, content: String) = "s3://$bucket/$key"
    override fun url(key: String) = "https://$bucket.s3.amazonaws.com/$key"
}

class S3StorageSigner(private val bucket: String) : StorageSigner {
    override fun sign(key: String, ttlSeconds: Long) =
        "https://$bucket.s3.amazonaws.com/$key?X-Amz-Expires=$ttlSeconds"
}

// ---------------------------------------------------------------------------------------------
// The configuration: one @Configuration per family.
// ---------------------------------------------------------------------------------------------

/**
 * `havingValue = "s3"` activates this whole family only when `storage.provider=s3`.
 *
 * Grouping a family in one `@Configuration` class is what makes this Abstract Factory rather than
 * a set of unrelated conditionals: the client and the signer are activated together, so a local
 * client can never be paired with an S3 signer.
 */
@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "s3")
class S3StorageConfig {

    @Bean
    fun storageClient(): StorageClient = S3StorageClient(bucket = "app-uploads")

    @Bean
    fun storageSigner(): StorageSigner = S3StorageSigner(bucket = "app-uploads")
}

/**
 * `matchIfMissing = true` makes local storage the default when nothing is configured — so a fresh
 * checkout starts and the test suite runs with no configuration at all.
 *
 * Always give one family this treatment. A configuration key with no default means every new
 * developer and every CI job starts with a startup failure.
 */
@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalStorageConfig {

    @Bean
    fun storageClient(): StorageClient = LocalStorageClient(root = "/tmp/uploads")

    @Bean
    fun storageSigner(): StorageSigner = LocalStorageSigner()
}

// ---------------------------------------------------------------------------------------------
// @ConditionalOnMissingBean — the "sensible default, overridable" idiom.
// ---------------------------------------------------------------------------------------------

interface UploadNamingStrategy {
    fun keyFor(filename: String): String
}

/**
 * This is how every Spring Boot auto-configuration works: provide a default *only if the application
 * has not defined its own*. Define your own `UploadNamingStrategy` bean anywhere and this one
 * silently steps aside.
 *
 * It is the right shape for a shared library or a starter module. Within a single application it is
 * usually over-engineering — just declare the bean.
 *
 * Ordering caveat: `@ConditionalOnMissingBean` is evaluated in registration order, so it is reliable
 * only in auto-configuration (which runs last) or with `@AutoConfigureAfter`. Using it between two
 * ordinary `@Configuration` classes is a race you can lose.
 */
@Configuration
class NamingConfig {

    @Bean
    @ConditionalOnMissingBean(UploadNamingStrategy::class)
    fun defaultNamingStrategy(): UploadNamingStrategy = object : UploadNamingStrategy {
        override fun keyFor(filename: String) = "uploads/${filename.lowercase()}"
    }
}

// ---------------------------------------------------------------------------------------------
// The consumer — written once, against the interfaces.
// ---------------------------------------------------------------------------------------------

/**
 * This service never learns which family it received. Switching providers is a configuration change
 * and a redeploy, with no code change and no recompilation.
 */
@Service
class UploadService(
    private val client: StorageClient,
    private val signer: StorageSigner,
    private val naming: UploadNamingStrategy,
) {
    fun upload(filename: String, content: String): String = client.put(naming.keyFor(filename), content)

    fun shareLink(filename: String, ttlSeconds: Long = 3_600): String =
        signer.sign(naming.keyFor(filename), ttlSeconds)

    fun activeProvider(): String = client.provider
}

/**
 * ## @Profile vs @ConditionalOnProperty
 *
 * - **`@Profile`** — coarse, environment-shaped: `dev`, `test`, `prod`. Good for swapping whole
 *   sets of infrastructure beans.
 * - **`@ConditionalOnProperty`** — fine-grained and independently switchable, and the value is
 *   visible in configuration rather than in a launch argument.
 *
 * Prefer properties. Profiles multiply: with four flags expressed as profiles you get sixteen
 * combinations, of which you have tested two.
 *
 * ## Diagnosing it
 *
 * When the wrong implementation is active, run with `--debug` (or `logging.level.org.springframework
 * .boot.autoconfigure=DEBUG`) to get the **condition evaluation report**, which lists every
 * condition and why it matched or did not. That report answers "why is this bean missing?" faster
 * than any amount of reading.
 *
 * ## The failure mode
 *
 * A typo in a property value silently selects no family, and the application fails with
 * `NoSuchBeanDefinitionException` — clear enough, but only at startup. Guard against it by giving
 * one family `matchIfMissing = true`, and by binding the provider name to an **enum** in
 * `@ConfigurationProperties` (`spring/configurationproperties`) so an invalid value is rejected with
 * a message naming the property.
 */
