#pragma once

#include <string>
#include <vector>
#include <map>
#include <functional>
#include <memory>

#define PREVIEW_RENDER_NAME "@camera_stream_id"
#define DEFAULT_DEVICE_UID "default_device"

namespace voip
{
    using CallId = std::string;
    using PeerId = std::string;

    // Состояния отправителя медиа-данных (необходимы для CallStateInternal.h в ядре)
    enum MediaSenderState {
        MSS_None = 0,
        MSS_Starting,
        MSS_Started,
        MSS_Stopping,
        MSS_Stopped,
        MSS_Error
    };

    enum DeviceType
    {
        AudioRecording = 0,
        AudioPlayback,
        VideoCapturing,
    };

    enum DeviceClass
    {
        Audio = 0,
        Video
    };

    enum VideoCaptureType
    {
        VC_DeviceCamera,
        VC_DeviceVirtualCamera,
        VC_DeviceDesktop
    };

    enum StaticImageIdEnum {
        Img_Logo = 0,
        Img_Avatar,
        Img_Username,
        Img_UserBackground,
        Img_UserForeground,
        Img_Max
    };

    enum TerminateReason {
        TR_HANGUP = 0,
        TR_REJECT,
        TR_BUSY,
        TR_HANDLED_BY_ANOTHER_INSTANCE,
        TR_UNAUTHORIZED,
        TR_ALLOCATE_FAILED,
        TR_ANSWER_TIMEOUT,
        TR_CONNECT_TIMEOUT,
        TR_NOT_FOUND,
        TR_BLOCKED_BY_CALLER_IS_STRANGER,
        TR_BLOCKED_BY_CALLEE_PRIVACY,
        TR_CALLER_MUST_BE_AUTHORIZED_BY_CAPCHA,
        TR_BAD_URI,
        TR_NOT_AVAILABLE_NOW,
        TR_PARTICIPANTS_LIMIT_EXCEEDED,
        TR_DURATION_LIMIT_EXCEEDED,
        TR_INTERNAL_ERROR
    };

    struct VoipDesc
    {
        bool first_notify_send = false;
        bool local_cam_en = false;
        bool local_aud_en = true;
        bool local_cam_allowed = true;
        bool local_aud_allowed = true;
        bool local_desktop_allowed = true;
        bool mute_en = false;
        bool incomingSoundsMuted = false;
        float volume = 1.0f;

        std::string aPlaybackDevice;
        std::string aRecordingDevice;
        std::string vCaptureDevice;
    };
}

namespace voip_manager
{
    using namespace voip;

    enum eNotificationTypes
    {
        kNotificationType_Undefined = 0,
        kNotificationType_CallCreated,
        kNotificationType_CallInAccepted,
        kNotificationType_CallConnected,
        kNotificationType_CallDisconnected,
        kNotificationType_CallDestroyed,
        kNotificationType_CallPeerListChanged,
        kNotificationType_MediaLocParamsChanged,
        kNotificationType_MediaRemVideoChanged,
        kNotificationType_MediaLocVideoDeviceChanged,
        kNotificationType_DeviceListChanged,
        kNotificationType_DeviceStarted,
        kNotificationType_DeviceVolChanged,
        kNotificationType_ShowVideoWindow,
        kNotificationType_VoipResetComplete,
        kNotificationType_VoipWindowRemoveComplete,
        kNotificationType_VoipWindowAddComplete,
        kNotificationType_VoiceDetect,
        kNotificationType_VoiceVadInfo,
        kNotificationType_ConfPeerDisconnected,
        kNotificationType_HideControlsWhenRemDesktopSharing,
    };

    struct VoipProxySettings
    {
        enum eProxyType { kProxyType_None = 0, kProxyType_Http, kProxyType_Socks4, kProxyType_Socks4a, kProxyType_Socks5 };
        eProxyType type;
        std::wstring serverUrl;
        std::wstring userName;
        std::wstring userPassword;
        VoipProxySettings() : type(kProxyType_None) { }
    };

    struct DeviceState { DeviceType type; std::string uid; bool success; };
    struct DeviceMute { DeviceType type; bool mute; };
    struct DeviceVol { DeviceType type; float volume; };
    
    struct Contact {
        voip::CallId call_id;
        std::string contact;
        bool remote_cam_enabled = false;
        bool remote_mic_enabled = false;
        bool remote_sending_desktop = false;
        bool connected_state = false;

        Contact() {}
        Contact(const voip::CallId& call_id_, const std::string& contact_) : call_id(call_id_), contact(contact_) {}
        bool operator==(const Contact& otherContact) const { 
            return otherContact.call_id == call_id && otherContact.contact == contact; 
        }
    };

    struct ContactEx {
        Contact contact;
        std::string call_type;
        std::string chat_id;
        bool current_call = false;
        bool incoming = false;
        int terminate_reason = 0;
        std::vector<void*> windows;
    };

    struct device_description {
        std::string uid;
        std::string name;
        DeviceType type = VideoCapturing;
        VideoCaptureType videoCaptureType = VC_DeviceCamera;
        bool is_default = false;
    };

    struct WindowBitmap { 
        void* data; 
        unsigned size; 
        unsigned w; 
        unsigned h; 
    };
    
    struct WindowParams {
        void* hwnd;
        voip::CallId call_id;
        bool isPrimary = false;
        bool isIncoming = false;
        float scale = 1.0f;
        WindowParams() : hwnd(nullptr), isPrimary(false), isIncoming(false), scale(1.0f) {}
    };

    struct VoipProtoMsg {
        std::string account_uid;
        int msg_type = 0;
        std::vector<char> data;
        VoipProtoMsg() : msg_type(0) {}
    };

    // Основной интерфейс управления VoIP для интеграции с Core
    class VoipManager
    {
    public:
        virtual ~VoipManager() = default;

        // Управление звонками
        virtual void call_create(const std::vector<std::string> &contacts, std::string &account, const bool video) = 0;
        virtual void call_stop() = 0;
        virtual void call_accept(const voip::CallId &call_id, const std::string &account, bool video) = 0;
        virtual void call_decline(const Contact& contact, bool busy, bool conference) = 0;
        virtual void call_terminate(const voip::CallId& call_id, TerminateReason reason) = 0;

        // Управление устройствами
        virtual void get_device_list(DeviceType device_type, std::vector<device_description>& dev_list) = 0;
        virtual void set_device(DeviceType device_type, const std::string& device_guid, bool force_reset) = 0;
        virtual void set_device_mute(DeviceType deviceType, bool mute) = 0;
        virtual bool get_device_mute(DeviceType deviceType) = 0;
        virtual void set_device_vol(DeviceType deviceType, float vol) = 0;
        virtual float get_device_vol(DeviceType deviceType) = 0;

        // Работа с окнами видео (для Android передается Surface/Texture ID через void*)
        virtual void window_add(WindowParams& windowParams) = 0;
        virtual void window_remove(void* hwnd) = 0;

        // Управление потоками
        virtual void media_video_en(bool enable) = 0;
        virtual void media_audio_en(bool enable) = 0;
        virtual bool is_video_en() = 0;
        virtual bool is_audio_en() = 0;

        // Обработка сигнального протокола
        virtual void ProcessVoipMsg(const std::string& account_uid, int voipIncomingMsg, const char *data, unsigned len) = 0;
        virtual void ProcessVoipAck(const VoipProtoMsg& msg, bool success) = 0;

        virtual void reset() = 0;
        virtual void update() = 0;
    };
}
