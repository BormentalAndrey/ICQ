# TODO: Ideally this should look for each Qt module separately, with each module's specific version,
# bypassing the Qt6 Config file, aka find_package(Qt6SpecificFoo) repated x times. But it's not
# critical.
find_package(Qt6 6.5.0
             REQUIRED COMPONENTS Core PngPrivate JpegPrivate FreetypePrivate HarfbuzzPrivate Concurrent Sql Network Xml Gui OpenGL Widgets OpenGLWidgets DeviceDiscoverySupportPrivate FbSupportPrivate InputSupportPrivate Test PrintSupport)
