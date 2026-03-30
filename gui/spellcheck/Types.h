#pragma once

#include <vector>
#include <utility>
#include <QString>

namespace spellcheck
{
    using Suggests = std::vector<QString>;
    struct WordStatus
    {
        bool isCorrect = false;
        bool isAdded = false;
    };
    using MisspellingRange = std::pair<int, int>;
    using Misspellings = std::vector<MisspellingRange>;
}
