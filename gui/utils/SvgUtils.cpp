#include "stdafx.h"
#include "SvgUtils.h"
#include "utils.h"

#include <QSvgRenderer>
#include <QPainter>
#include <QFileInfo>
#include <QTransform>
#include <QBrush>
#include <cmath>
#include <algorithm>
#include <limits>

namespace
{
    // Вспомогательная функция для преобразования контейнеров цветов в актуальные QColor
    Utils::ColorLayers colorsFromContainers(const Utils::ColorContainerLayers& _containers)
    {
        Utils::ColorLayers result;
        result.reserve(_containers.size());
        std::transform(
            _containers.begin(),
            _containers.end(),
            std::back_inserter(result),
            [](const std::pair<QString, Styling::ColorContainer>& _container) 
            { 
                return std::make_pair(_container.first, _container.second.actualColor()); 
            });
        return result;
    }

    // Преобразование параметров темы в фиксированные цвета
    Utils::ColorLayers colorsFromParameters(const Utils::ColorParameterLayers& _parameters)
    {
        Utils::ColorLayers result;
        result.reserve(_parameters.size());
        std::transform(
            _parameters.begin(),
            _parameters.end(),
            std::back_inserter(result),
            [](const auto& _parameter) 
            { 
                return std::make_pair(_parameter.first, _parameter.second.color()); 
            });
        return result;
    }

    // Создание контейнеров из параметров для отслеживания изменений темы
    Utils::ColorContainerLayers containersFromParameters(const Utils::ColorParameterLayers& _parameters)
    {
        Utils::ColorContainerLayers result;
        result.reserve(_parameters.size());
        std::transform(
            _parameters.begin(),
            _parameters.end(),
            std::back_inserter(result),
            [](const auto& _parameter) 
            { 
                return std::make_pair(_parameter.first, Styling::ColorContainer { _parameter.second }); 
            });
        return result;
    }
} // namespace

namespace Utils
{

QPixmap renderSvg(const QString& _filePath, const QSize& _scaledSize, const QColor& _tintColor, const KeepRatio _keepRatio)
{
    if (Q_UNLIKELY(!QFileInfo::exists(_filePath)))
        return QPixmap();

    QSvgRenderer renderer(_filePath);
    if (!renderer.isValid())
        return QPixmap();

    const auto defSize = renderer.defaultSize();
    if (Q_UNLIKELY(_scaledSize.isEmpty() && defSize.isEmpty()))
        return QPixmap();

    QSize resultSize;
    QRectF bounds;

    if (!_scaledSize.isEmpty())
    {
        resultSize = scale_bitmap(_scaledSize);
        bounds = QRectF(QPointF(0, 0), QSizeF(resultSize));

        if (_keepRatio == KeepRatio::yes)
        {
            const double wRatio = defSize.width() / (double)resultSize.width();
            const double hRatio = defSize.height() / (double)resultSize.height();
            constexpr double epsilon = std::numeric_limits<double>::epsilon();

            if (Q_UNLIKELY(std::fabs(wRatio - hRatio) > epsilon))
            {
                const auto resultCenter = bounds.center();
                QSizeF s = QSizeF(defSize).scaled(QSizeF(resultSize), Qt::KeepAspectRatio);
                bounds.setSize(s);
                bounds.moveCenter(resultCenter);
            }
        }
    }
    else
    {
        resultSize = scale_bitmap_with_value(defSize);
        bounds = QRectF(QPointF(0, 0), QSizeF(resultSize));
    }

    QPixmap pixmap(resultSize);
    pixmap.fill(Qt::transparent);

    {
        QPainter painter(&pixmap);
        painter.setRenderHint(QPainter::Antialiasing);
        painter.setRenderHint(QPainter::SmoothPixmapTransform);
        
        renderer.render(&painter, bounds);

        if (_tintColor.isValid() && _tintColor.alpha() > 0)
        {
            painter.setCompositionMode(QPainter::CompositionMode_SourceIn);
            painter.fillRect(pixmap.rect(), _tintColor);
        }
    }

    check_pixel_ratio(pixmap);
    return pixmap;
}

QPixmap renderSvgScaled(const QString& _resourcePath, const QSize& _unscaledSize, const QColor& _tintColor, const KeepRatio _keepRatio)
{
    return renderSvg(_resourcePath, scale_value(_unscaledSize), _tintColor, _keepRatio);
}

QPixmap renderSvgLayered(const QString& _filePath, const ColorLayers& _layers, const QSize& _scaledSize)
{
    if (Q_UNLIKELY(!QFileInfo::exists(_filePath)))
        return QPixmap();

    QSvgRenderer renderer(_filePath);
    if (!renderer.isValid())
        return QPixmap();

    const auto defSize = scale_bitmap_with_value(renderer.defaultSize());
    if (Q_UNLIKELY(_scaledSize.isEmpty() && defSize.isEmpty()))
        return QPixmap();

    QSize resultSize = _scaledSize.isEmpty() ? defSize : scale_bitmap(_scaledSize);
    QPixmap pixmap(resultSize);
    pixmap.fill(Qt::transparent);

    QTransform scaleTransform;
    if (!_scaledSize.isEmpty() && defSize != resultSize)
    {
        const double s = double(resultSize.width()) / defSize.width();
        scaleTransform.scale(s, s);
    }

    {
        QPainter painter(&pixmap);
        painter.setRenderHint(QPainter::Antialiasing);
        painter.setRenderHint(QPainter::SmoothPixmapTransform);

        if (!_layers.empty())
        {
            for (const auto& [layerName, layerColor] : _layers)
            {
                if (!layerColor.isValid() || layerColor.alpha() == 0)
                    continue;

                if (!renderer.elementExists(layerName))
                    continue;

                const auto elTransform = renderer.transformForElement(layerName);
                const auto elBounds = renderer.boundsOnElement(layerName);
                
                // Масштабируем и позиционируем слой
                const auto mappedRect = scale_bitmap_with_value(elTransform.mapRect(elBounds));
                auto layerBounds = scaleTransform.mapRect(mappedRect);
                
                // Корректировка координат для субпиксельного рендеринга
                layerBounds.moveTopLeft(QPointF(
                    fscale_bitmap_with_value(layerBounds.topLeft().x()), 
                    fscale_bitmap_with_value(layerBounds.topLeft().y())
                ));

                QPixmap layerPix(resultSize);
                layerPix.fill(Qt::transparent);

                {
                    QPainter layerPainter(&layerPix);
                    renderer.render(&layerPainter, layerName, layerBounds);

                    layerPainter.setCompositionMode(QPainter::CompositionMode_SourceIn);
                    layerPainter.fillRect(layerPix.rect(), layerColor);
                }
                painter.drawPixmap(0, 0, layerPix);
            }
        }
        else
        {
            renderer.render(&painter, QRectF(QPointF(0,0), QSizeF(resultSize)));
        }
    }

    check_pixel_ratio(pixmap);
    return pixmap;
}

// Реализация классов контейнеров

BasePixmap::BasePixmap(const QPixmap& _pixmap)
    : pixmap_(_pixmap)
{}

void BasePixmap::setPixmap(const QPixmap& _pixmap)
{
    pixmap_ = _pixmap;
}

QPixmap BaseStyledPixmap::actualPixmap()
{
    updatePixmap();
    return cachedPixmap();
}

void BaseStyledPixmap::updatePixmap()
{
    if (canUpdate())
        setPixmap(generatePixmap());
}

BaseStyledPixmap::BaseStyledPixmap(const QPixmap& _pixmap, const QString& _path, const QSize& _size)
    : BasePixmap(_pixmap)
    , path_(_path)
    , size_(_size)
{}

StyledPixmap::StyledPixmap()
    : StyledPixmap(QString(), QSize(), Styling::ColorParameter())
{}

StyledPixmap::StyledPixmap(const QString& _resourcePath, const QSize& _scaledSize, const Styling::ColorParameter& _tintColor, KeepRatio _keepRatio)
    : BaseStyledPixmap(renderSvg(_resourcePath, _scaledSize, _tintColor.color(), _keepRatio), _resourcePath, _scaledSize)
    , color_(_tintColor)
    , keepRatio_(_keepRatio)
{}

StyledPixmap StyledPixmap::scaled(const QString& _resourcePath, const QSize& _unscaledSize, const Styling::ColorParameter& _tintColor, KeepRatio _keepRatio)
{
    return StyledPixmap(_resourcePath, scale_value(_unscaledSize), _tintColor, _keepRatio);
}

bool StyledPixmap::canUpdate() const
{
    return color_.canUpdateColor();
}

QPixmap StyledPixmap::generatePixmap()
{
    return renderSvg(path(), size(), color_.actualColor(), keepRatio_);
}

bool operator==(const StyledPixmap& _lhs, const StyledPixmap& _rhs)
{
    return _lhs.path() == _rhs.path() && _lhs.size() == _rhs.size() && _lhs.color_ == _rhs.color_ && _lhs.keepRatio_ == _rhs.keepRatio_;
}

LayeredPixmap::LayeredPixmap()
    : LayeredPixmap(QString(), ColorParameterLayers(), QSize())
{}

LayeredPixmap::LayeredPixmap(const QString& _filePath, const ColorParameterLayers& _layers, const QSize& _scaledSize)
    : BaseStyledPixmap(renderSvgLayered(_filePath, colorsFromParameters(_layers), _scaledSize), _filePath, _scaledSize)
    , layers_(containersFromParameters(_layers))
{}

bool LayeredPixmap::canUpdate() const
{
    return std::any_of(layers_.begin(), layers_.end(), [](const auto& _layer) 
    { 
        return _layer.second.canUpdateColor(); 
    });
}

QPixmap LayeredPixmap::generatePixmap()
{
    return renderSvgLayered(path(), colorsFromContainers(layers_), size());
}

} // namespace Utils
