#pragma once

#include "environment.h"
#include <string>
#include <vector>
#include <cassert>

#if !defined(InOut)
#define InOut
#endif

#if !defined(Out)
#define Out
#endif

#if !defined(UNUSED_ARG)
#define UNUSED_ARG(arg) ((void)arg)
#endif

#if !defined(__FUNCTION__)
#define __FUNCTION__ ""
#endif

#define __DISABLE(x) {}

#define __STRA(x) _STRA(x)
#define _STRA(x) #x

#define __STRW(x) _STRW(x)
#define _STRW(x) L#x

#define __LINEA__ __STRA(__LINE__)
#define __LINEW__ __STRW(__LINE__)

#define __FUNCLINEA__ __FUNCTION__ "(#" __LINEA__ ")"
#define __FUNCLINEW__ __FUNCTIONW__ L"(#" __LINEW__ L")"

#define __FILELINEA__ __FILE__ "(" __LINEA__ ")"
#define __FILELINEW__ __FILEW__ L"(" __LINEW__ L")"

#define __TODOA__ " [TODO]: "
#define __TODOW__ L" [TODO]: "

#ifdef __APPLE__
#undef __TODOA__
#define __TODOA__ ""
#undef __TODOW__
#define __TODOW__ ""
#undef __FILELINEA__
#define __FILELINEA__ ""
#undef __FILELINEW__
#define __FILELINEW__ ""
#undef __FUNCLINEA__
#define __FUNCLINEA__ ""
#undef __FUNCLINEW__
#define __FUNCLINEW__ ""
#endif

#ifndef _countof
#define _countof(array) (sizeof(array) / sizeof(array[0]))
#endif

namespace logutils {
constexpr const char *yn(const bool v) noexcept { return (v ? "yes" : "no"); }
constexpr const char *tf(const bool v) noexcept { return (v ? "true" : "false"); }
}

namespace core {
constexpr unsigned long long BYTE = 1ULL;
constexpr unsigned long long KILOBYTE = 1024ULL * BYTE;
constexpr unsigned long long MEGABYTE = 1024ULL * KILOBYTE;
constexpr unsigned long long GIGABYTE = 1024ULL * MEGABYTE;

const std::string KILOBYTE_STR = "kb";
const std::string MEGABYTE_STR = "mb";
const std::string GIGABYTE_STR = "gb";
const std::string MILLISECONDS_STR = "ms";

namespace stats {
using event_prop_key_type = std::string;
using event_prop_val_type = std::string;
using event_prop_kv_type = std::pair<event_prop_key_type, event_prop_val_type>;
using event_props_type = std::vector<event_prop_kv_type>;

constexpr int32_t msg_pending_delay_s = 5;

inline std::string round_interval(const long long _min_val, const long long _value, const long long _step, const long long _max_value) {
  if ((_value < _min_val) || (_value > _max_value) || _step <= 0) return std::string();
  auto steps = (_value - _min_val) / _step;
  auto start = _min_val + steps * _step;
  auto end = (start + _step > _max_value) ? _max_value : start + _step;
  return std::to_string(start) + '-' + std::to_string(end);
}

inline std::string memory_size_interval(size_t _bytes) {
  const unsigned long long b = static_cast<unsigned long long>(_bytes);
  if (b <= 100ULL * MEGABYTE) return round_interval(0, b / MEGABYTE, 100, 100) + MEGABYTE_STR;
  if (b <= 500ULL * MEGABYTE) return round_interval(100, b / MEGABYTE, 50, 500) + MEGABYTE_STR;
  if (b <= 1ULL * GIGABYTE) return round_interval(500, b / MEGABYTE, 100, 1024) + MEGABYTE_STR;
  return "more1" + GIGABYTE_STR;
}

inline std::string disk_space_interval(long long _bytes) {
  if (_bytes > static_cast<long long>(1500ULL * MEGABYTE)) return "1500mb +";
  if (_bytes < static_cast<long long>(100ULL * MEGABYTE)) return "< 100mb";
  return round_interval(100, _bytes / static_cast<long long>(MEGABYTE), 100, 1500) + MEGABYTE_STR;
}

inline std::string traffic_size_interval(size_t _bytes) {
  const unsigned long long b = static_cast<unsigned long long>(_bytes);
  if (b <= 100ULL * KILOBYTE) return round_interval(0, b / KILOBYTE, 100, 100) + KILOBYTE_STR;
  if (b <= 500ULL * KILOBYTE) return round_interval(100, b / KILOBYTE, 400, 500) + KILOBYTE_STR;
  if (b <= MEGABYTE) return round_interval(500, b / KILOBYTE, 500, 1024) + KILOBYTE_STR;
  if (b <= 5ULL * MEGABYTE) return round_interval(1, b / MEGABYTE, 1, 5) + MEGABYTE_STR;
  if (b <= 500ULL * MEGABYTE) return round_interval(50, b / MEGABYTE, 50, 500) + MEGABYTE_STR;
  return "more500" + MEGABYTE_STR;
}
}
}
