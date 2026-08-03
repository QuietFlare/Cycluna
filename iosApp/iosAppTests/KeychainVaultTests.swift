import XCTest
import Shared
@testable import iosApp

/// Exercises the REAL Keychain-backed `KeyVault` actual from the shared core.
///
/// `./gradlew :shared:jvmTest` can only reach the JVM in-memory actual, so until now the
/// iOS implementation — the one that will hold the journal encryption key — had never been
/// run by any test. It needs a device/simulator, so it lives here.
final class KeychainVaultTests: XCTestCase {

    private var vault: KeyVault!
    /// Namespaced so a failed run can't collide with anything the app itself stored.
    private let key = "test.cycluna.keyvault.\(UUID().uuidString)"

    override func setUpWithError() throws {
        try super.setUpWithError()
        // Exported as `KeyVault_iosKt` because the actual lives in `KeyVault.ios.kt` —
        // Kotlin/Native derives the Swift name from the file, not the declaration.
        vault = KeyVault_iosKt.defaultKeyVault()

        // The Keychain needs an application-identifier entitlement, which an UNSIGNED
        // test host does not have — CI builds with CODE_SIGNING_ALLOWED=NO, so every
        // SecItemAdd there fails with errSecMissingEntitlement and every read is nil.
        //
        // Skip the whole class rather than only the three asserting tests: the tests
        // that assert nil would otherwise pass vacuously and report coverage for a
        // subsystem that never ran.
        let probe = "test.cycluna.keyvault.probe.\(UUID().uuidString)"
        vault.set(key: probe, value: "probe")
        let reachable = vault.get(key: probe) != nil
        vault.remove(key: probe)
        try XCTSkipUnless(
            reachable,
            "Keychain unreachable — the test host is unsigned (CODE_SIGNING_ALLOWED=NO). "
                + "Run signed, e.g. via the local `xcodebuild test` in CLAUDE.md, to cover the vault."
        )
    }

    override func tearDown() {
        vault.remove(key: key)
        vault = nil
        super.tearDown()
    }

    func testStoredValueComesBack() {
        vault.set(key: key, value: "letter-key-abc123")
        XCTAssertEqual(vault.get(key: key), "letter-key-abc123")
    }

    func testMissingKeyIsNil() {
        XCTAssertNil(vault.get(key: "test.cycluna.keyvault.definitely-absent"))
    }

    func testSettingTwiceOverwritesRatherThanDuplicating() {
        // The Keychain rejects a duplicate add, so `set` deletes first. Without that, the
        // second write would silently no-op and the old secret would persist.
        vault.set(key: key, value: "first")
        vault.set(key: key, value: "second")
        XCTAssertEqual(vault.get(key: key), "second")
    }

    func testRemoveDeletesTheValue() {
        vault.set(key: key, value: "temporary")
        vault.remove(key: key)
        XCTAssertNil(vault.get(key: key))
    }

    func testRemovingSomethingAbsentIsHarmless() {
        vault.remove(key: key)
        vault.remove(key: key)
        XCTAssertNil(vault.get(key: key))
    }

    func testRoundTripsUnicodeAndLongValues() {
        // Journal keys are base64, but the vault must not mangle anything it's handed.
        let value = "🌙 clé-secrète " + String(repeating: "k", count: 2048)
        vault.set(key: key, value: value)
        XCTAssertEqual(vault.get(key: key), value)
    }
}
