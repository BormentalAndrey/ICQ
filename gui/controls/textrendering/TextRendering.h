#pragma once
#include <QString>
#include <QVector>

namespace Data {
    using FStringView = QStringView;
}

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
