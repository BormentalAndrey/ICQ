host_build {
    QT_CPU_FEATURES.x86_64 = cx16 mmx sse sse2 sse3 ssse3 sse4.1
} else {
    QT_CPU_FEATURES.arm64 = neon
}
QT.global_private.enabled_features = android-style-assets reduce_exports neon posix_fallocate alloca_h alloca system-zlib gui network printsupport sql testlib widgets xml openssl dlopen relocatable cross_compile force_debug_info precompile_header neon
QT.global_private.disabled_features = use_bfd_linker use_gold_linker use_lld_linker use_mold_linker gc_binaries developer-build no-prefix private_tests debug no_direct_extern_access x86intrin sse2 sse3 ssse3 sse4_1 sse4_2 avx f16c avx2 avx512f avx512er avx512cd avx512pf avx512dq avx512bw avx512vl avx512ifma avx512vbmi avx512vbmi2 aesni vaes rdrnd rdseed shani mips_dsp mips_dspr2 arm_crc32 arm_crypto alloca_malloc_h stack-protector-strong stdlib-libcpp dbus dbus-linked libudev intelcet
CONFIG += cross_compile force_debug_info precompile_header neon
QT_COORD_TYPE = double
QT_BUILD_PARTS = libs

QMAKE_LIBS_ZLIB = -lz
