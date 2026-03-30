#pragma once
#include <QString>
#include <QVector>

#include "../../types/fstring.h" // Подключаем настоящий Data::FStringView

class TextRendering {
public:
    struct Range { int start; int length; };
    struct FormattedText { QString text; QVector<Range> formats; };
    
    static FormattedText parseFormatting(const Data::FStringView& text) {
        FormattedText result;
        result.text = text.toString();
        return result;
    }
};
