#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QtAndroidExtras/QtAndroid>

int main(int argc, char *argv[])
{
    QGuiApplication app(argc, argv);

    // Инициализация Android-специфичных разрешений (если нужно)
    QtAndroid::requestPermissionsSync({"android.permission.INTERNET",
                                       "android.permission.WRITE_EXTERNAL_STORAGE"});

    QQmlApplicationEngine engine;

    // Загрузка главного QML-файла
    const QUrl url(QStringLiteral("qrc:/qml/main.qml"));
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreated,
                     &app, [url](QObject *obj, const QUrl &objUrl) {
        if (!obj && url == objUrl)
            QCoreApplication::exit(-1);
    }, Qt::QueuedConnection);

    engine.load(url);

    return app.exec();
}
