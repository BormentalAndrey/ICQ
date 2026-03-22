#include "network_change_notifier_win.h"
#include <QNetworkConfigurationManager>
#include <QObject>

namespace core {
namespace network {

class NetworkChangeNotifier {
public:
    static NetworkChangeNotifier* instance() {
        static NetworkChangeNotifier inst;
        return &inst;
    }
    
    void start() {
#ifdef PLATFORM_WIN
        NotifyAddrChange(&hWaitHandle_, &overlapped_);
#else
        static QNetworkConfigurationManager manager;
        QObject::connect(&manager, &QNetworkConfigurationManager::onlineStateChanged,
                         [](bool isOnline) {
                             if (auto* inst = instance())
                                 inst->notifyListeners(isOnline);
                         });
#endif
    }
    
    void notifyListeners(bool isOnline) {
        // Уведомляем слушателей
    }
    
private:
    HANDLE hWaitHandle_;
    OVERLAPPED overlapped_;
};

} // namespace network
} // namespace core
