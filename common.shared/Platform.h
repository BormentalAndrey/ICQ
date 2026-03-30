#ifndef PLATFORM_H
#define PLATFORM_H

#if defined(__ANDROID__) || defined(ANDROID)
    #define PLATFORM_ANDROID 1
#elif defined(_WIN32) || defined(_WIN64)
    #define PLATFORM_WIN 1
#elif defined(__linux__)
    #define PLATFORM_LINUX 1
#elif defined(__APPLE__)
    #define PLATFORM_MAC 1
#endif

// Подмена WinAPI типов для кроссплатформенной сборки (в т.ч. Android)
#ifndef PLATFORM_WIN
    #include <stdint.h>
    #include <stddef.h>

    typedef uint32_t DWORD;
    typedef uint16_t WORD;
    typedef uint8_t BYTE;
    typedef void* HANDLE;
    typedef void* HWND;
    typedef void* LPVOID;
    typedef int BOOL;
    typedef uintptr_t WPARAM;
    typedef intptr_t LPARAM;
    typedef long LONG;
    typedef unsigned int UINT;

    #ifndef TRUE
        #define TRUE 1
    #endif
    #ifndef FALSE
        #define FALSE 0
    #endif

    #define WINAPI
    #define CALLBACK

    // Базовая эмуляция Sleep для POSIX-систем
    #include <unistd.h>
    #define Sleep(x) usleep((x) * 1000)
#endif

#endif // PLATFORM_H
