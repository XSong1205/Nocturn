# Embedded Node.js runtime

Nocturn's bridge is an independent Kotlin/JNI integration. It does not include
SPlayer code, Capacitor, a WebView, or the Cordova plugin.

## Pinned upstream

- Project: <https://github.com/nodejs-mobile/nodejs-mobile>
- Release: **v18.20.4**, the latest official Android release inspected during this implementation.
- Binary: <https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v18.20.4/nodejs-mobile-v18.20.4-android.zip>
- ZIP SHA-256: `bd7321eaa1a7602fbe0bb87302df2d79d87835cf4363fbdd17c350dbb485c2af`
- License: <https://raw.githubusercontent.com/nodejs-mobile/nodejs-mobile/v18.20.4/LICENSE>
- License SHA-256: `66233b87054d6bca9b0c2f23b027bb2caf1ebd961a6f981616d098dade33af34`

`node scripts/prepare-node-runtime.mjs` downloads and verifies these files,
extracts only the required headers and the `arm64-v8a` / `x86_64` libraries,
and writes `local-api-android/build/node-runtime/manifest.json` containing
per-file checksums and ELF load-segment alignment. A cached extraction is
checked against those checksums before reuse. No dependency install scripts
or third-party archive executables are used.

The release ZIP is about 55 MiB. The two selected uncompressed libraries are
about 122 MiB together, so production APKs should be split by ABI. Downloads
live in `.cache/node-runtime/`; generated files and binaries belong in build
outputs, not source control.

## JNI contract

```kotlin
package dev.nocturn.localapi

internal object NodeNative {
    init { System.loadLibrary("nocturn_node") }

    @JvmStatic
    external fun start(
        entryPath: String,
        dataDirectory: String,
        port: Int,
        token: String,
    ): Int
}
```

Call `start` on a dedicated application-owned background thread. It blocks
until the Node event loop terminates. The entry must already be installed as
an absolute file path in app-private storage, and the data directory must
exist. The bridge sets `NOCTURN_API_PORT`, `NOCTURN_API_TOKEN`, and
`NOCTURN_DATA_DIR` in the Android process. `HOME` and `TMPDIR` point to the
private data directory so upstream configuration files cannot escape it.

Port `0` requests an OS-assigned port. JavaScript writes its bound port into
`runtime-ready.json` in the private data directory; Kotlin deletes stale
readiness data before launch, reads the new file, then verifies server
health and authenticates a request. No credential is placed in a filename,
process argument, readiness file, or log message.

**Node can be started at most once per Android process.** A second call
returns `-1`; configuration failures return `-2`, environment failures `-3`,
and pipe setup failures `-4`. Any such failure after launch is terminal for
that process. A returned Node exit status is also terminal. Application
code must not silently attempt to call `node::Start` again.

The bridge uses mutable contiguous argument storage for Node and retains no
JNI string pointers. Standard output and standard error are drained to avoid
pipe blockage. Only a fixed readiness message or generic diagnostic notice
is logged; arbitrary upstream text is suppressed because exceptions may
include cookies or request parameters.

## Android build

The CMake project imports the pinned `libnode.so` for the active Android ABI.
Use NDK 28 and configure `-DANDROID_STL=c++_shared`: upstream `libnode.so`
depends on `libc++_shared.so`. The bridge itself uses 16 KiB linker alignment.
Do not add a second duplicate `libnode.so` via `jniLibs`; Android Gradle Plugin
packages the CMake imported dependency.

The generated `licenses/nodejs-mobile-LICENSE` contains Node's MIT notice
and the bundled dependencies' license notices. Include this file in APK
assets and expose it through the app's eventual third-party notices screen.

## Verified limitations

Both official 64-bit libraries have **4096-byte ELF LOAD alignment**, verified
with NDK `llvm-readelf -l` and independently parsed by the preparation script.
They must **not** be advertised as supporting Android devices with a 16 KiB
page size. The Kotlin manager should detect page size before loading native
libraries and return an explicit unsupported-runtime state on those devices.
Changing APK ZIP alignment or the bridge's linker flags cannot repair this
prebuilt library; a newer runtime or a source rebuild and device validation
are required before shipping to 16 KiB devices / applicable app stores.

This upstream release embeds Node 18.20.4, which is an old, end-of-life Node
branch. This is a development integration, not a claim of production runtime
readiness. A maintained Node mobile build and Android security review remain
release requirements. Node dependency JavaScript is built for Node 18;
packages requiring Node 20+ must be bundled/polyfilled or pinned compatibly
and verified separately.

The local HTTP server still needs an internet connection for online music
API calls. Its runtime being embedded does not make remote content offline.
