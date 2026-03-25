#pragma once

#include <QDataStream>
#include <QString>
#include <vector>
#include <string>
#include <variant>
#include <optional>

// Добавляем инклуды RapidJSON здесь, чтобы они были доступны везде
#include <rapidjson/document.h>

namespace core::tools::json_helper
{
    // Сериализация базовых типов
    template<typename T>
    void serialize(QDataStream& out, const T& value) { out << value; }

    template<typename T>
    void deserialize(QDataStream& in, T& value) { in >> value; }

    // Специализация для std::string
    inline void serialize(QDataStream& out, const std::string& value) {
        out << QString::fromStdString(value);
    }

    inline void deserialize(QDataStream& in, std::string& value) {
        QString tmp; in >> tmp; 
        value = tmp.toStdString();
    }

    // Специализация для std::vector
    template<typename T>
    void serialize(QDataStream& out, const std::vector<T>& vec) {
        out << static_cast<int>(vec.size());
        for (const auto& item : vec) serialize(out, item);
    }

    template<typename T>
    void deserialize(QDataStream& in, std::vector<T>& vec) {
        int size; in >> size; 
        vec.resize(size);
        for (int i = 0; i < size; ++i) deserialize(in, vec[i]);
    }

    // Объявление функции сортировки (реализация в .cpp)
    void sort_json_keys_by_name(rapidjson::Value& _node);

} // namespace core::tools::json_helper
