#ifndef __BINARYSTREAM_H_
#define __BINARYSTREAM_H_

#pragma once
#include <fstream>
#include <vector>
#include <cstring>
#include <iterator>
#include <algorithm>
#include <string>
#include "zlib.h"
#include "strings.h" // Для from_utf16

namespace core::tools {
class stream {
public:
  virtual ~stream() = default;
  virtual void write(const char *_data, int64_t _size) = 0;
  virtual int64_t all_size() const noexcept = 0;
  virtual int64_t available() const noexcept = 0;
  virtual char *get_data() const noexcept = 0;
  virtual const char *get_data_for_log() const noexcept = 0;
  virtual void swap(stream *_stream) noexcept = 0;
  virtual void close() {}
};

class binary_stream final : public stream {
  using data_buffer = std::vector<char>;
  mutable data_buffer buffer_;
  mutable int64_t input_cursor_ = 0;
  mutable int64_t output_cursor_ = 0;

public:
  binary_stream() = default;
  void swap(stream *_stream) noexcept override {
    if (auto bs = dynamic_cast<binary_stream *>(_stream)) {
        std::swap(bs->input_cursor_, input_cursor_);
        std::swap(bs->output_cursor_, output_cursor_);
        std::swap(bs->buffer_, buffer_);
    }
  }

  int64_t available() const noexcept override { return input_cursor_ - output_cursor_; }
  int64_t all_size() const noexcept override { return static_cast<int64_t>(buffer_.size()); }
  char *get_data() const noexcept override { return buffer_.empty() ? nullptr : buffer_.data(); }

  void set_input(int64_t _pos) const noexcept { input_cursor_ = _pos; }
  void set_output(int64_t _pos) const noexcept { output_cursor_ = _pos; }

  void reset() {
    input_cursor_ = 0;
    output_cursor_ = 0;
    buffer_.clear();
  }

  void reset_out() {
    output_cursor_ = 0;
  }

  char *alloc_buffer(int64_t _size) {
    const auto size_need = input_cursor_ + _size;
    if (static_cast<size_t>(size_need) > buffer_.size())
      buffer_.resize(static_cast<size_t>(size_need) * 2);
    char *out = &buffer_[static_cast<size_t>(input_cursor_)];
    input_cursor_ += _size;
    return out;
  }

  void write(const char *_data, int64_t _size) override {
    if (_size <= 0) return;
    const auto size_need = input_cursor_ + _size;
    if (static_cast<size_t>(size_need) > buffer_.size())
      buffer_.resize(static_cast<size_t>(size_need) + 1, '\0');
    memcpy(&buffer_[static_cast<size_t>(input_cursor_)], _data, static_cast<size_t>(_size));
    input_cursor_ += _size;
  }

  char *read(int64_t _size) const {
    if (_size <= 0 || available() < _size) return nullptr;
    char *out = &buffer_[static_cast<size_t>(output_cursor_)];
    output_cursor_ += _size;
    return out;
  }

  const char *get_data_for_log() const noexcept override {
    return (available() <= 0) ? nullptr : &buffer_[static_cast<size_t>(output_cursor_)];
  }
  
  bool is_compressed() const noexcept {
    if (buffer_.size() < 4) return false;
    uint8_t b0 = static_cast<uint8_t>(buffer_[0]);
    uint8_t b1 = static_cast<uint8_t>(buffer_[1]);
    return (b0 == 0x78 && (b1 == 0x9C || b1 == 0x01)) || (b0 == 0x1F && b1 == 0x8B) || (b0 == 0x28 && b1 == 0xB5);
  }

  bool load_from_file(const std::wstring& _path) {
    #ifdef _WIN32
        std::ifstream file(_path, std::ios::binary | std::ios::ate);
    #else
        std::string path_u8 = core::tools::from_utf16(_path);
        std::ifstream file(path_u8, std::ios::binary | std::ios::ate);
    #endif
    
    if (!file.is_open()) return false;
    
    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    
    if (size > 0) {
        buffer_.resize(static_cast<size_t>(size));
        if (file.read(buffer_.data(), size)) {
            input_cursor_ = size;
            output_cursor_ = 0;
            return true;
        }
    }
    return false;
  }
};
}
#endif
