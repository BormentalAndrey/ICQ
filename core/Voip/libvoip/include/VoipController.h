#pragma once

#include <string>
#include <vector>
#include <memory>

// Подключаем определения типов, которые мы исправили выше
#include "../../VoipManagerDefines.h"

// Предполагаем наличие SDK заголовков, если они есть в системе
#ifdef HAS_VOIP_SDK
#include <voip/voip3.h>
#endif

namespace voip {
    // Внешние зависимости (forward declarations)
    class VoipEngine;
    class IVoipVideoWindowRenderer;
    struct CallAppData;
    struct CallParticipantInfo;
    struct CallStateInternal;
    enum class SoundEvent;
}

namespace camera {
    namespace CameraEnumerator { struct CameraInfo; using CameraInfoList = std::vector<CameraInfo>; }
    namespace Masker { enum class Status; }
}

namespace base { class Invoker; }
namespace rtc { class Thread; }

namespace im {

enum AudioDeviceType {
    AudioDeviceRecording = 0,
    AudioDevicePlayback,
};

enum class LogLevel {
    Info, Warning, Error, None
};

struct AudioDeviceInfo {
    std::string name;
    std::string uid;
    bool is_default;
};

using AudioDeviceInfoList = std::vector<AudioDeviceInfo>;

class VoipControllerObserver {
public:
    virtual ~VoipControllerObserver() = default;
    
    // Теперь voip::CallId и voip::TerminateReason распознаются корректно
    virtual void onPreOutgoingCall(const voip::CallId &call_id, const std::string &user_id) = 0;
    virtual void onStartedOutgoingCall(const voip::CallId &call_id, const std::string &user_id) = 0;
    virtual void onIncomingCall(const voip::CallId &call_id, const std::string &from_user_id, bool is_video) = 0;
    virtual void onRinging(const voip::CallId &call_id) = 0;
    virtual void onOutgoingCallAccepted(const voip::CallId &call_id, const voip::CallAppData &app_data) = 0;
    virtual void onConnecting(const voip::CallId &call_id) = 0;
    virtual void onCallActive(const voip::CallId &call_id) = 0;
    virtual void onCallTerminated(const voip::CallId &call_id, bool terminated_locally, voip::TerminateReason reason) = 0;
    virtual void onCallParticipantsUpdate(const voip::CallId &call_id, const std::vector<voip::CallParticipantInfo> &participants) = 0;

    virtual void onCameraListUpdated(const camera::CameraEnumerator::CameraInfoList &camera_list) {}
    virtual void onAudioDevicesListUpdated(AudioDeviceType type, const AudioDeviceInfoList &audio_list) {}
};

class VoipController {
public:
    VoipController(VoipControllerObserver& controllerObserver);
    virtual ~VoipController();

    static void EnableLogToDir(const std::string& path, const std::string& log_prefix, size_t max_size, LogLevel level);
    
    // Исправлено: передача корректного типа CallId
    bool Initialize(const std::string &config_json);

    void Action_EnableMicrophone(const voip::CallId &call_id, bool enabled);
    void Action_EnableCamera(const voip::CallId &call_id, bool enabled);

    void Action_StartOutgoingCall(const std::string& to_user_id, bool is_video_call);
    void Action_AcceptIncomingCall(const voip::CallId &call_id, bool is_video_call);
    void Action_DeclineCall(const voip::CallId &call_id, bool busy);

    void VideoWindowAdd(const voip::CallId& call_id, std::shared_ptr<voip::IVoipVideoWindowRenderer> renderer);
    void VideoWindowRemove(const voip::CallId& call_id, std::shared_ptr<voip::IVoipVideoWindowRenderer> renderer);
    
    void MuteSounds(bool mute);
    void SetAudioDeviceMute(AudioDeviceType type, bool mute);
    void SetAudioDeviceVolume(AudioDeviceType type, float volume);

private:
    std::unique_ptr<voip::VoipEngine>   _voip;
    std::unique_ptr<base::Invoker>      _invoker;
    std::unique_ptr<rtc::Thread>        _work_thread;
    VoipControllerObserver&             _observer;
};

} // namespace im
