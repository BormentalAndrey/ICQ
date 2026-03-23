!host_build {
    QMAKE_CFLAGS    += --sysroot=$$[QT_SYSROOT]
    QMAKE_CXXFLAGS  += --sysroot=$$[QT_SYSROOT]
    QMAKE_LFLAGS    += --sysroot=$$[QT_SYSROOT]
}
host_build {
    QT_ARCH = x86_64
    QT_BUILDABI = x86_64-little_endian-lp64
    QT_TARGET_ARCH = arm64
    QT_TARGET_BUILDABI = arm64-little_endian-lp64
} else {
    QT_ARCH = arm64
    QT_BUILDABI = arm64-little_endian-lp64
    QT_LIBCPP_ABI_TAG = 
}
QT.global.enabled_features = shared cross_compile appstore-compliant c++11 c++14 c++17 c++1z c99 c11 signaling_nan thread future concurrent opensslv30 shared cross_compile shared c++11 c++14 c++17 c++1z reduce_exports openssl
QT.global.disabled_features = static pkg-config debug_and_release separate_debug_info simulator_and_device rpath force_asserts framework c++20 c++2a c++2b c++2b reduce_relocations wasm-simd128 wasm-exceptions zstd dbus openssl-linked opensslv11
QT.global.disabled_features += release build_all
QT_CONFIG += shared no-pkg-config c++11 c++14 c++17 c++1z reduce_exports openssl release
CONFIG +=  shared cross_compile plugin_manifest
QT_VERSION = 6.5.0
QT_MAJOR_VERSION = 6
QT_MINOR_VERSION = 5
QT_PATCH_VERSION = 0

QT_CLANG_MAJOR_VERSION = 14
QT_CLANG_MINOR_VERSION = 0
QT_CLANG_PATCH_VERSION = 6
QT_EDITION = OpenSource
QT_LICHECK =
QT_RELEASE_DATE = 2023-03-29
