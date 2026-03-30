#pragma once

#include <functional>
#include <memory>
#include <vector>
#include <optional>
#include <map>

// Qt core & gui
#include <QString>
#include <QFont>
#include <QPainter>
#include <QPoint>
#include <QRect>
#include <QSize>

#include "textrendering/TextRendering.h"
#include "../styles/StyleVariable.h"
#include "../styles/ThemeColor.h"

// ИСПРАВЛЕНИЕ: Вместо class MentionMap, подключаем заголовок, где определен MentionMap и Data::FString
#include "../types/message.h"

namespace Data
{
    struct LinkInfo;
}

namespace Ui
{
    // ИСПРАВЛЕНИЕ: Предварительное объявление UI-типов, которых не было видно
    enum class KeyToSendMessage;
    enum class ShortcutsCloseAction;
    enum class ShortcutsSearchAction;

    using highlightsV = std::vector<QString>;

    // Определения типов для корректной сборки под Android
    enum class HorAligment { LEFT, RIGHT, CENTER, JUSTIFY };
    enum class VerPosition { TOP, MIDDLE, BOTTOM };
    enum class LineBreakType { PREFER_WIDTH, PREFER_SPACES, FORCE_BREAK };
    enum class EmojiSizeType { REGULAR, SMALL, MEDIUM, LARGE };
    enum class LinksStyle { PLAIN, UNDERLINED };
    enum class LinksVisible { SHOW_LINKS, HIDE_LINKS };
    enum class ProcessLineFeeds { KEEP_LINE_FEEDS, REMOVE_LINE_FEEDS };
    enum class CallType { USUAL, EXTENDED };
    enum class TextType { VISIBLE, SOURCE };

    [[nodiscard]] QString getEllipsis();

    enum class ParseBackticksPolicy
    {
        KeepBackticks,
        ParseSingles,
        ParseTriples,
        ParseSinglesAndTriples,
    };

    namespace TextRendering
    {
        class BaseDrawingBlock;
        using BaseDrawingBlockPtr = std::unique_ptr<BaseDrawingBlock>;
        enum class BlockType : int;
        struct TextWordWithBoundary;
        class TextWord;

        class TextUnit
        {
        public:
            struct InitializeParameters
            {
                InitializeParameters(QFont _font, Styling::ColorParameter _colorKey)
                    : font_ { std::move(_font) }
                    , monospaceFont_ { font_ }
                    , color_ { std::move(_colorKey) }
                {}

                void setFonts(const QFont& _font)
                {
                    font_ = _font;
                    monospaceFont_ = _font;
                }

                QFont font_;
                QFont monospaceFont_;
                Styling::ColorParameter color_;
                Styling::ThemeColorKey linkColor_;
                Styling::ThemeColorKey selectionColor_;
                Styling::ThemeColorKey highlightColor_;
                HorAligment align_ = HorAligment::LEFT;
                LineBreakType lineBreak_ = LineBreakType::PREFER_WIDTH;
                EmojiSizeType emojiSizeType_ = EmojiSizeType::REGULAR;
                LinksStyle linksStyle_ = LinksStyle::PLAIN;
                int maxLinesCount_ = -1;
            };

            TextUnit();
            TextUnit(const Data::FString& _text, const Data::MentionMap& _mentions, LinksVisible _showLinks, ProcessLineFeeds _processLineFeeds, EmojiSizeType _emojiSizeType);

            void setText(const QString& _text, const Styling::ThemeColorKey& _color = {});
            void setText(const Data::FString& _text, const Styling::ThemeColorKey& _color = {});

            void setMentions(const Data::MentionMap& _mentions);
            const Data::MentionMap& getMentions() const;

            void setTextAndMentions(const QString& _text, const Data::MentionMap& _mentions);
            void setTextAndMentions(const Data::FString& _text, const Data::MentionMap& _mentions);

            void elide(int _width);
            [[nodiscard]] bool isElided() const;

            using condition_callback_t = std::function<bool(BaseDrawingBlockPtr&)>;
            using modify_callback_t = std::function<void(BaseDrawingBlockPtr&, size_t)>;

            void forEachBlockOfType(BlockType _blockType, const modify_callback_t& _modifyCallback);
            [[nodiscard]] bool removeBlocks(BlockType _blockType, const condition_callback_t& _removeConditionCallback);
            [[nodiscard]] bool replaceBlock(size_t _atIndex, BaseDrawingBlockPtr&& _newBlock, const condition_callback_t& _replaceConditionCallback);
            [[nodiscard]] bool insertBlock(BaseDrawingBlockPtr&& _block, size_t _atIndex);

            void assignBlock(BaseDrawingBlockPtr&& _block);
            void assignBlocks(std::vector<BaseDrawingBlockPtr>&& _blocks);

            void setOffsets(int _horOffset, int _verOffset);
            inline void setOffsets(const QPoint& _offsets) { setOffsets(_offsets.x(), _offsets.y()); }

            [[nodiscard]] int horOffset() const;
            [[nodiscard]] int verOffset() const;
            [[nodiscard]] QPoint offsets() const;

            void draw(QPainter& _painter, VerPosition _pos = VerPosition::TOP);
            void drawSmart(QPainter& _painter, int _center);

            int getHeight(int _width, CallType _calltype = CallType::USUAL);
            void evaluateDesiredSize();

            void init(const InitializeParameters& _parameters);

            void setLastLineWidth(int _width);
            size_t getLinesCount() const;

            void select(QPoint _from, QPoint _to);
            void selectAll();
            void fixSelection();
            void clearSelection();
            void releaseSelection();
            [[nodiscard]] bool isSelected() const;

            void clicked(const QPoint& _p);
            void doubleClicked(const QPoint& _p, bool _fixSelection, std::function<void(bool)> _callback = std::function<void(bool)>());

            [[nodiscard]] bool isOverLink(const QPoint& _p) const;
            [[nodiscard]] Data::LinkInfo getLink(const QPoint& _p) const;

            [[nodiscard]] std::optional<TextWordWithBoundary> getWordAt(QPoint) const;
            [[nodiscard]] bool replaceWordAt(const Data::FString& _old, const Data::FString& _new, QPoint _p);

            [[nodiscard]] Data::FString getSelectedText(TextType _type = TextType::VISIBLE) const;
            [[nodiscard]] QString getText() const;
            [[nodiscard]] Data::FString getTextInstantEdit() const;
            [[nodiscard]] Data::FString getSourceText() const;
            [[nodiscard]] bool isAllSelected() const;
            [[nodiscard]] int desiredWidth() const;

            int sourceTextWidth() const;
            int textWidth() const;

            void applyLinks(const std::map<QString, QString>& _links);
            void applyFontToLinks(const QFont& _font);

            [[nodiscard]] bool contains(const QPoint& _p) const;
            [[nodiscard]] QSize cachedSize() const;

            void setColor(const Styling::ColorParameter& _color);
            void setLinkColor(const Styling::ThemeColorKey& _color);
            void setSelectionColor(const Styling::ThemeColorKey& _color);
            void setHighlightedTextColor(const Styling::ThemeColorKey& _color);
            void setHighlightColor(const Styling::ThemeColorKey& _color);

            [[nodiscard]] Styling::ThemeColorKey getColorKey() const;
            [[nodiscard]] QColor getColor() const;
            [[nodiscard]] Styling::ThemeColorKey getLinkColorKey() const;

            [[nodiscard]] HorAligment getAlign() const;
            void setAlign(HorAligment _align);

            void setColorForAppended(const Styling::ThemeColorKey& _color);
            [[nodiscard]] const QFont& getFont() const;

            void append(std::unique_ptr<TextUnit>&& _other);
            void appendBlocks(std::unique_ptr<TextUnit>&& _other);

            void setLineSpacing(int _spacing);
            void setHighlighted(const bool _isHighlighted);
            void setHighlighted(const highlightsV& _entries);
            void setUnderline(const bool _enabled);

            int getLastLineWidth() const;
            int getMaxLineWidth() const;

            void setEmojiSizeType(const EmojiSizeType _emojiSizeType);
            LinksVisible linksVisibility() const;
            bool needsEmojiMargin() const;
            size_t getEmojiCount() const;
            bool isEmpty() const;
            void disableCommands();

            void startSpellChecking(std::function<bool()> isAlive, std::function<void(bool)> onFinish);
            static bool isSkippableWordForSpellChecking(const TextWordWithBoundary& w);

            int64_t blockId() const noexcept { return blockId_; }
            [[nodiscard]] bool hasEmoji() const;
            void setShadow(const int _offsetX, const int _offsetY, const QColor& _color);
            std::vector<QRect> getLinkRects() const;
            bool mightStretchForLargerWidth() const;

            const std::vector<BaseDrawingBlockPtr>& blocks() const { return blocks_; }

        protected:
            Data::FString sourceText_;
            TextUnit(std::vector<BaseDrawingBlockPtr>&& _blocks, const Data::MentionMap& _mentions, LinksVisible _showLinks, ProcessLineFeeds _processLineFeeds, EmojiSizeType _emojiSizeType);
            void setSourceText(const Data::FString& _text, ProcessLineFeeds _processLineFeeds);
            void appendToSourceText(TextUnit& _suffix);

        private:
            void initializeBlocks();
            void initializeBlocksAndSelectedTextColor();
            [[nodiscard]] QPoint mapPoint(QPoint) const;
            void updateColors();

        private:
            std::vector<BaseDrawingBlockPtr> blocks_;
            int horOffset_ = 0;
            int verOffset_ = 0;

            Data::MentionMap mentions_;
            LinksVisible showLinks_ = LinksVisible::SHOW_LINKS;
            ProcessLineFeeds processLineFeeds_ = ProcessLineFeeds::KEEP_LINE_FEEDS;
            EmojiSizeType emojiSizeType_ = EmojiSizeType::REGULAR;

            QFont font_;
            QFont monospaceFont_;
            Styling::ColorContainer color_;
            Styling::ColorContainer linkColor_;
            Styling::ColorContainer selectionColor_;
            Styling::ColorContainer highlightColor_;
            Styling::ColorContainer highlightTextColor_;
            QSize cachedSize_;
            HorAligment align_ = HorAligment::LEFT;
            int maxLinesCount_ = -1;
            int appended_ = -1;
            LineBreakType lineBreak_ = LineBreakType::PREFER_WIDTH;
            LinksStyle linksStyle_ = LinksStyle::PLAIN;
            int lineSpacing_ = 0;
            bool needsEmojiMargin_ = false;

            int64_t blockId_ = 0;

            std::shared_ptr<bool> guard_;

            // ИСПРАВЛЕНИЕ: VerPosition находится в Ui::, а не в Ui::TextRendering
            VerPosition lastVerPosition_ = VerPosition::TOP;
        };

        using TextUnitPtr = std::unique_ptr<TextUnit>;

        TextUnitPtr MakeTextUnit(
            const Data::FString& _text,
            const Data::MentionMap& _mentions = {},
            LinksVisible _showLinks = LinksVisible::SHOW_LINKS,
            ProcessLineFeeds _processLineFeeds = ProcessLineFeeds::KEEP_LINE_FEEDS,
            EmojiSizeType _emojiSizeType = EmojiSizeType::REGULAR,
            ParseBackticksPolicy _backticksPolicy = ParseBackticksPolicy::KeepBackticks);

        TextUnitPtr MakeTextUnit(
            const QString& _text,
            const Data::MentionMap& _mentions = {},
            LinksVisible _showLinks = LinksVisible::SHOW_LINKS,
            ProcessLineFeeds _processLineFeeds = ProcessLineFeeds::KEEP_LINE_FEEDS,
            EmojiSizeType _emojiSizeType = EmojiSizeType::REGULAR);

        bool InsertOrUpdateDebugMsgIdBlockIntoUnit(TextUnitPtr& _textUnit, qint64 _id, size_t _atPosition = 0);
        bool InsertDebugMsgIdBlockIntoUnit(TextUnitPtr& _textUnit, qint64 _id, size_t _atPosition = 0);
        bool UpdateDebugMsgIdBlock(TextUnitPtr& _textUnit, qint64 _newId);
        bool RemoveDebugMsgIdBlocks(TextUnitPtr& _textUnit);
        void ExtractLinkWords(TextUnitPtr& _textUnit, std::vector<const TextWord*>& _words);
    } // namespace TextRendering
} // namespace Ui
