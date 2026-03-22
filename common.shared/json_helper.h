#pragma once
#include <QDataStream>
#include <QString>
#include <vector>
#include <string>

namespace core {
namespace tools {
namespace json_helper {

template<typename T>
void serialize(QDataStream& out, const T& value) { out << value; }

template<typename T>
void deserialize(QDataStream& in, T& value) { in >> value; }

inline void serialize(QDataStream& out, const std::string& value) {
    out << QString::fromStdString(value);
}

inline void deserialize(QDataStream& in, std::string& value) {
    QString tmp; in >> tmp; value = tmp.toStdString();
}

template<typename T>
void serialize(QDataStream& out, const std::vector<T>& vec) {
    out << (int)vec.size();
    for (const auto& item : vec) serialize(out, item);
}

template<typename T>
void deserialize(QDataStream& in, std::vector<T>& vec) {
    int size; in >> size; vec.resize(size);
    for (int i = 0; i < size; ++i) deserialize(in, vec[i]);
}

} // namespace json_helper
} // namespace tools
} // namespace core
