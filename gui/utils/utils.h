#pragma once

#include "../../corelib/enumerations.h"
#include "../types/message.h"
#include "../types/filesharing_download_result.h"
#include "../controls/TextUnit.h"
#include "StringComparator.h"
#include "UrlUtils.h"
#include "SvgUtils.h"
#include "../styles/ThemeParameters.h"

// Базовые инклюды Qt, которые работают на Android
#include <QObject>
#include <QBuffer>
#include <QIODevice>
#include <QNetworkProxy>
#include <QEvent>
#include <QDebug>
#include <typeinfo>
#include <QStringView>
#include <QDataStream>

// Исключаем QtWidgets для Android, так как их нет в составе сборки
#if !defined(Q_OS_ANDROID)
#include <QMimeData>
#include <QStyle>
#include <QWidget>
#endif

class QApplication;
class QScreen;

#ifdef _WIN32
#include <qt_windows.h>
QPixmap qt_pixmapFromWinHBITMAP(HBITMAP bitmap, int hbitmapFormat = 0);
#endif //_WIN32

namespace Ui
{
    class GeneralDialog;
    class CheckBox;
    class MessagesScrollArea;
    class HistoryControlPage;
    class PageBase;
    enum class ConferenceType;

    // Эти типы должны быть доступны всегда для сигнатур функций
    enum class KeyToSendMessage {
        Enter,
        CtrlEnter,
        ShiftEnter
    };

    enum class ShortcutsCloseAction {
        CloseCurrent,
        CloseAll,
        None
    };

    enum class ShortcutsSearchAction {
        SearchInChat,
        SearchGlobal,
        None
    };
}

namespace Debug
{
#if !defined(Q_OS_ANDROID)
    void debugFormattedText(QMimeData* _mime);
#endif

    void dumpQtEvent(QEvent* _event, QStringView _context = {});

    template <typename This, typename FuncName>
    void debugMemberCallImpl(This _this, FuncName _funcName)
    {
#ifdef _DEBUG
        qDebug() << typeid(*_this).name() << _funcName;
#else
        Q_UNUSED(_this)
        Q_UNUSED(_funcName)
#endif
    }
}

namespace Utils
{
#if !defined(Q_OS_ANDROID)
    // QStyle и QWidget не существуют в Android-сборке ядра
    void SetProxyStyle(QWidget* _widget, QStyle* _style);
#endif

    template <typename T>
    QByteArray serialize(const T& _val)
    {
        QByteArray array;
        QBuffer b(&array);
        b.open(QIODevice::WriteOnly);
        QDataStream s(&b);
        s << _val;
        return array;
    }

    using SendKeysIndex = std::vector<std::pair<QString, Ui::KeyToSendMessage>>;
    const SendKeysIndex& getSendKeysIndex();

    using ShortcutsCloseActionsList = std::vector<std::pair<QString, Ui::ShortcutsCloseAction>>;
    const ShortcutsCloseActionsList& getShortcutsCloseActionsList();
    QString getShortcutsCloseActionName(Ui::ShortcutsCloseAction _action);

    using ShortcutsSearchActionsList = std::vector<std::pair<QString, Ui::ShortcutsSearchAction>>;
    const ShortcutsSearchActionsList& getShortcutsSearchActionsList();

    struct ProxySettings
    {
        static QNetworkProxy::ProxyType proxyType(core::proxy_type _type);
    };

    class CloseWindowInfo
    {
    public:
        enum class CallLinkFrom
        {
            Unknown,
            Messenger,
            VideoConference
        };

        CloseWindowInfo() : from_(CallLinkFrom::Unknown) {}
        CloseWindowInfo(CallLinkFrom _from, const QString& _aimId) : from_(_from), aimId_(_aimId) {}

        int64_t seqRequestWithLoader_;
        CallLinkFrom from_;
        QString aimId_;
    };

    template<typename T, typename U>
    int indexOf(T start, T end, const U& val)
    {
        int result = -1;
        auto it = std::find(start, end, val);
        if (it != end)
            result = std::distance(start, it);
        return result;
    }

    enum class ROAction
    {
        Ban,
        Allow
    };
    QString getReadOnlyString(ROAction _action, bool _isChannel);
    QString getMembersString(int _number, bool _isChannel);

    QString getFormattedTime(std::chrono::milliseconds _duration);

    constexpr int defaultMouseWheelDelta() noexcept { return 120; }

    bool isLineBreak(QChar _ch);
}

Q_DECLARE_METATYPE(Utils::CloseWindowInfo)

namespace Logic
{
    enum class Placeholder
    {
        Contacts,
        Recents
    };
    void updatePlaceholders(const std::vector<Placeholder> &_locations = {Placeholder::Contacts, Placeholder::Recents});
}

namespace FileSharing
{
    enum class FileType
    {
        archive, xls, html, keynote, pdf, ppt, txt, doc, image, video, audio, unknown
    };
}
