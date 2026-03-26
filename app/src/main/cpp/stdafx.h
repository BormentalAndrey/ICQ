#pragma once

#ifdef __cplusplus
// Стандартная библиотека
#include <memory>
#include <string>
#include <vector>
#include <map>
#include <set>
#include <algorithm>
#include <functional>
#include <chrono>

// Qt
#include <QString>
#include <QObject>
#include <QDebug>
#include <QByteArray>
#include <QVector>

// Android Logging
#include <android/log.h>
#define LOG_TAG "DZIN_CORE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Макросы совместимости (если код использует Windows-типы)
#ifndef _WIN32
    typedef int32_t HRESULT;
    #define S_OK ((HRESULT)0L)
    #define S_FALSE ((HRESULT)1L)
    #define FAILED(hr) (((HRESULT)(hr)) < 0)
    #define SUCCEEDED(hr) (((HRESULT)(hr)) >= 0)
#endif

#endif
