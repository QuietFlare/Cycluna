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

    override func setUp() {
        super.setUp()
        // Exported as `KeyVault_iosKt` because the actual lives in `KeyVault.ios.kt` —
        // Kotlin/Native derives the Swift name from the file, not the declaration.
        vault = KeyVault_iosKt.defaultKeyVault()
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
