import SwiftUI

/// Third-party attribution for the code Cycluna ships.
///
/// This is a licence obligation, not a courtesy: Apache 2.0 §4 requires the attribution
/// notices to travel with any distributed work containing the code, and the Kotlin runtime
/// plus the kotlinx libraries are linked into the app binary.
///
/// Distinct from the repo's own MIT licence, which covers the *source* and never reaches
/// someone who installs from the App Store.
///
/// ⚠️ Keep `libraries` in step with `shared/build.gradle.kts`. `AcknowledgementsTest` in the
/// shared module fails if a runtime dependency is added there and not listed here.
struct AcknowledgementsView: View {

    struct Library: Identifiable {
        let id = UUID()
        let name: String
        /// The coordinate as it appears in the build file, so the two can be checked against
        /// each other. Empty for code that arrives implicitly, like the Kotlin runtime.
        let coordinate: String
        let copyright: String
        let licence: String
    }

    static let libraries: [Library] = [
        .init(name: "kotlinx-datetime",
              coordinate: "org.jetbrains.kotlinx:kotlinx-datetime",
              copyright: "Copyright © 2019–2026 JetBrains s.r.o.",
              licence: "Apache License 2.0"),
        .init(name: "kotlinx.serialization",
              coordinate: "org.jetbrains.kotlinx:kotlinx-serialization-json",
              copyright: "Copyright © 2017–2026 JetBrains s.r.o.",
              licence: "Apache License 2.0"),
        .init(name: "Kotlin standard library & Kotlin/Native runtime",
              coordinate: "",
              copyright: "Copyright © 2010–2026 JetBrains s.r.o. and Kotlin Programming Language contributors",
              licence: "Apache License 2.0"),
    ]

    private static let apacheURL = URL(string: "https://www.apache.org/licenses/LICENSE-2.0")!

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("Acknowledgements")
                    .font(.cyclunaSerif(30)).foregroundStyle(Theme.ink)

                Text("Cycluna is built with these open-source libraries.")
                    .font(.body).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)

                ForEach(Self.libraries) { library in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(library.name)
                            .font(.headline).foregroundStyle(Theme.primary)
                        Text(library.copyright)
                            .font(.footnote).foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(library.licence)
                            .font(.footnote).foregroundStyle(Theme.inkSoft)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Apache License 2.0")
                        .font(.headline).foregroundStyle(Theme.primary)
                    // The boilerplate notice the licence itself asks distributors to carry.
                    Text("""
                    Licensed under the Apache License, Version 2.0 (the "License"); you may not \
                    use these files except in compliance with the License. Unless required by \
                    applicable law or agreed to in writing, software distributed under the \
                    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS \
                    OF ANY KIND, either express or implied. See the License for the specific \
                    language governing permissions and limitations under the License.
                    """)
                        .font(.footnote).foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Link("Read the full licence", destination: Self.apacheURL)
                        .font(.footnote).tint(Theme.primary)
                }
                .padding(.top, 4)

                Text("Cycluna's own source is open, published under the MIT License.")
                    .font(.caption).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(20)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Acknowledgements")
        .navigationBarTitleDisplayMode(.inline)
    }
}
