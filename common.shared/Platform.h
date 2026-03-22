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

#endif
