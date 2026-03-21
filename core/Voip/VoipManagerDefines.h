#pragma once

#include <string>
#include <vector>
#include <map>
#include <functional>
#include <memory>

// Подключаем оригинальный voip3.h из libvoip, чтобы взять оттуда MediaSenderState и TerminateReason
#include <voip/voip3.h>

#define PREVIEW_RENDER_NAME "@camera_stream_id"
#define DEFAULT_DEVICE_UID "default_device"

namespace voip
{
    using CallId = std::string;
    using PeerId = std::string;

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

    struct CallStartParams {
        bool video = false;
        bool attach = false;
        std::string call_type;
    };

    class VoipManager
    {
    public:
        virtual ~VoipManager() = default;

        virtual void call_create(const std::vector<std::string> &contacts, std::string &account, const bool video) = 0;
        virtual void call_stop() = 0;
        virtual void call_accept(const voip::CallId &call_id, const std::string &account, bool video) = 0;
        virtual void call_decline(const Contact& contact, bool busy, bool conference) = 0;
        // Используем TerminateReason из voip3.h
        virtual void call_terminate(const voip::CallId& call_id, voip::TerminateReason reason) = 0;

        virtual void get_device_list(DeviceType device_type, std::vector<device_description>& dev_list) = 0;
        virtual void set_device(DeviceType device_type, const std::string& device_guid, bool force_reset) = 0;
        virtual void set_device_mute(DeviceType deviceType, bool mute) = 0;
        virtual bool get_device_mute(DeviceType deviceType) = 0;
        virtual void set_device_vol(DeviceType deviceType, float vol) = 0;
        virtual float get_device_vol(DeviceType deviceType) = 0;

        virtual void window_add(WindowParams& windowParams) = 0;
        virtual void window_remove(void* hwnd) = 0;

        virtual void media_video_en(bool enable) = 0;
        virtual void media_audio_en(bool enable) = 0;
        virtual bool is_video_en() = 0;
        virtual bool is_audio_en() = 0;

        virtual void ProcessVoipMsg(const std::string& account_uid, int voipIncomingMsg, const char *data, unsigned len) = 0;
        virtual void ProcessVoipAck(const VoipProtoMsg& msg, bool success) = 0;

        virtual void reset() = 0;
        virtual void update() = 0;
    };
}
