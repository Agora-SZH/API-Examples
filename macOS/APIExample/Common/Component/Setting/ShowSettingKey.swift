//
//  ShowSettingManager.swift
//  AgoraEntScenarios
//
//  Created by FanPengpeng on 2022/11/16.
//

import Foundation
import AgoraRtcKit

enum ShowAudioSettingKey {
    /**
     * AI降噪等级
     *
     */
    @objc public enum AINS_STATE: Int {
        case off
        case mid
        case high
    }
    
    /**
     * 回声消除等级
     * small 对应UI上的零回声
     * middle 对应UI上的标准
     * long 对应UI上的流畅
     */
    @objc public enum AEC_FILTER_LENGTH: Int {
        case small
        case middle
        case long
    }

    case AINS(state: AINS_STATE)   // AI降噪
    case AEC(isOn: Bool?)  // 回声消除
    case AEC_LENGTH(length: AEC_FILTER_LENGTH)
    case AEC_FILTER_TYPE(type: Int)

    var boolValue: Bool {
        return UserDefaults.standard.bool(forKey: self.caseKey())
    }

    var intValue: Int {
        return UserDefaults.standard.integer(forKey: self.caseKey())
    }
    
    func caseKey() -> String {
        switch self {
        case .AINS(_):
            return "AINS"
        case .AEC(_):
            return "AEC"
        case .AEC_LENGTH(_):
            return "AEC_LENGTH"
        case .AEC_FILTER_TYPE(_):
            return "AEC_FILTER_TYPE"
        }
    }

    func writeValue() {
        var value : Any
        switch self {
        case .AINS(let state):
            value = state.rawValue
        case .AEC(let isOn):
            value = isOn ?? true
        case .AEC_LENGTH(let length):
            value = length.rawValue
        case .AEC_FILTER_TYPE(let type):
            value = type
        }
        UserDefaults.standard.set(value, forKey: self.caseKey())
        UserDefaults.standard.synchronize()
    }
}


// 超分倍数
enum SRType: Int {
    case none = -1
    case x1 = 6
    case x1_33 = 7
    case x1_5 = 8
    case x2 = 3
    case x_sharpen = 11
    case x_superQuality = 20
}

enum ShowAgoraSRType: String, CaseIterable {
    case x1 = "x1"
    case x1_33 = "x1.33"
    case x1_5 = "x1.5"
    case x2 = "x2"
    
    var typeValue: SRType {
        switch self {
        case .x1:
            return .x1
        case .x1_33:
            return .x1_33
        case .x1_5:
            return .x1_5
        case .x2:
            return .x2
        }
    }
}

enum ShowAgoraRenderMode: String, CaseIterable {
    case hidden = "hidden"
    case fit = "fit"
    
    var modeValue: AgoraVideoRenderMode {
        switch self {
        case .hidden:
            return .hidden
        case .fit:
            return .fit
        }
    }
}

enum ShowAgoraEncode: String, CaseIterable {
    case hard = "硬编"
    case soft = "软编"
    
    var encodeValue: Bool {
        switch self {
        case .hard:
            return true
        case .soft:
            return false
        }
    }
}

enum ShowAgoraCodeCType: String, CaseIterable {
    case h265 = "h265"
    case h264 = "h264"
    
    var typeValue: Int {
        switch self {
        case .h265:
            return 3
        case .h264:
            return 2
        }
    }
}

enum ShowAgoraVideoDimensions: String, CaseIterable {
    case _320x240 = "320x240"
    case _480x360 = "480x360"
    case _960x540 = "960x540"
    case _960x720 = "960x720"
    case _1280x720 = "1280x720"
    case _1920x1080 = "1920x1080"
    case _2560x1440 = "2560x1440"
    case _3840x2160 = "3840x2160"
     
    var sizeValue: CGSize {
        let arr: [String] = rawValue.split(separator: "x").compactMap{"\($0)"}
        guard let first = arr.first, let width = Float(first), let last = arr.last, let height = Float(last) else {
            return CGSize(width: 360, height: 640)
        }
        return CGSize(width: CGFloat(width), height: CGFloat(height))
    }
}

enum ShowAgoraCaptureVideoDimensions: Int, CaseIterable {
    
    case _1080P = 1080
    case _720P = 720
    case _540P = 540
    case _480P = 480
    case _360P = 360
    case _270P = 270
     
    var sizeValue: CGSize {
        if rawValue == 480 {
            return CGSize(width: 480, height: 854)
        }
        return CGSize(width: CGFloat(rawValue), height: CGFloat(rawValue) * 1280.0 / 720.0)
    }
    
    var valueTitle: String {
        return "\(rawValue)P"
    }
    
    var levelTitle: String {
        switch self {
        case ._1080P:
            return "极清"
        case ._720P:
            return "超清"
        case ._540P:
            return "高清"
        case ._480P:
            return "标清"
        case ._360P:
            return "流畅"
        case ._270P:
            return "低清"
        }
    }
}

extension AgoraVideoFrameRate {
    func stringValue() -> String {
        return "\(rawValue) fps"
    }
}

let fpsItems: [AgoraVideoFrameRate] = [
    .fps10,
    .fps15,
    .fps24,
    .fps30,
    .fps60
]

enum ShowSettingKey: String, CaseIterable {
    
    enum KeyType {
        case aSwitch
        case segment
        case slider
        case label
    }
    
    case lowlightEnhance        // 暗光增强
    case colorEnhance           // 色彩增强
    case videoDenoiser          // 降噪
    case beauty                 // 美颜
    case PVC                    // pvc
    case SR                     // 超分
    case BFrame                 // b帧
    case videoEncodeSize       // 视频编码分辨率
    case FPS                    // 帧率
    case H265                   // h265
    case videoBitRate           // 视频码率
    case earmonitoring          // 耳返
    case recordingSignalVolume  // 人声音量
    case musincVolume           // 音乐音量
    case audioBitRate           // 音频码率
    case captureVideoSize       // 采集分辨率
    
    var title: String {
        switch self {
        case .lowlightEnhance:
            return "暗光增强"
        case .colorEnhance:
            return "色彩增强"
        case .videoDenoiser:
            return "视频降噪"
        case .beauty:
            return "美颜"
        case .PVC:
            return "码率节省"
        case .SR:
            return "超分"
        case .BFrame:
            return "B帧"
        case .videoEncodeSize:
            return "编码分辨率"
        case .FPS:
            return "编码帧率"
        case .videoBitRate:
            return "码率"
        case .H265:
            return "H265"
        case .earmonitoring:
            return "耳返"
        case .recordingSignalVolume:
            return "人声音量"
        case .musincVolume:
            return "音乐音量"
        case .audioBitRate:
            return "音频码率"
        case .captureVideoSize:
            return ""
        }
    }
    
    // 类型
    var type: KeyType {
        switch self {
        case .lowlightEnhance:
            return .aSwitch
        case .colorEnhance:
            return .aSwitch
        case .videoDenoiser:
            return .aSwitch
        case .beauty:
            return .aSwitch
        case .PVC:
            return .aSwitch
        case .SR:
            return .aSwitch
        case .BFrame:
            return .aSwitch
        case .videoEncodeSize:
            return .label
        case .FPS:
            return .label
        case .H265:
            return .aSwitch
        case .videoBitRate:
            return .slider
        case .earmonitoring:
            return .aSwitch
        case .recordingSignalVolume:
            return .slider
        case .musincVolume:
            return .slider
        case .audioBitRate:
            return .label
        case .captureVideoSize:
            return .label
        }
    }

    // 弹窗提示文案
    var tips: String {
        switch self {
        case .lowlightEnhance:
            return "打开该开关后，将自动对暗光下的图像进行处理，通过智能补光等措施增加一定清晰度和亮度，最终实现图像质量提升"
        case .colorEnhance:
            return "打开该开关后，将自动调整视频画面的色彩饱和度，使得画面色彩更加鲜明逼真，提升视觉主观感受"
        case .videoDenoiser:
            return "打开该开关后，将自动去除视频中的噪点，使视频画面看起来更加的清晰"
        case .PVC:
            return "打开开关后，将自动实现在同等画质情况下，降低码率的效果"
        case .SR:
            return "打开开关后，将使用一系列画质提升策略，包含但不限于超分等技术，主要保证在同等码率下，提升高品质图像质量"
        case .H265:
            return "打开开关后，将使用一系列画质提升策略，包含但不限于h265的编码技术，主要保证在同等码率下，提升高"
        case .videoEncodeSize:
            return "修改编码分辨率后，请在观众端查看画质变化效果"
        case .FPS:
            return "为保证最佳画质效果，当机型为中低端机默认15fps及以下帧率生效，超过15fps不生效"
        default:
            return ""
        }
    }
    
    // slider的取值区间
    var sliderValueScope: (Float, Float) {
        switch self {
        case .videoBitRate:
            return (200, 4000)
        case .recordingSignalVolume:
            return (0, 100)
        case .musincVolume:
            return (0, 100)
        default:
            return (0,0)
        }
    }
    
    var boolValue: Bool {
        return UserDefaults.standard.bool(forKey: self.rawValue)
    }
    
    var floatValue: Float {
        return UserDefaults.standard.float(forKey: self.rawValue)
    }
    
    var intValue: Int {
        return UserDefaults.standard.integer(forKey: self.rawValue)
    }
    
    func writeValue(_ value: Any?){
        UserDefaults.standard.set(value, forKey: self.rawValue)
        UserDefaults.standard.synchronize()
    }
}

