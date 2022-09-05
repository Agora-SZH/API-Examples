//
//  ScreenShare.swift
//  APIExample
//
//  Created by 张乾泽 on 2020/4/17.
//  Copyright © 2020 Agora Corp. All rights reserved.
//
import UIKit
import AGEVideoLayout
import AgoraRtcKit
import ReplayKit

class ScreenShareEntry : UIViewController
{
    @IBOutlet weak var joinButton: UIButton!
    @IBOutlet weak var channelTextField: UITextField!
    let identifier = "ScreenShare"
    
    override func viewDidLoad() {
        super.viewDidLoad()
    }
    
    @IBAction func doJoinPressed(sender: UIButton) {
        guard let channelName = channelTextField.text else {return}
        //resign channel text field
        channelTextField.resignFirstResponder()
        
        let storyBoard: UIStoryboard = UIStoryboard(name: identifier, bundle: nil)
        // create new view controller every time to ensure we get a clean vc
        guard let newViewController = storyBoard.instantiateViewController(withIdentifier: identifier) as? BaseViewController else {return}
        newViewController.title = channelName
        newViewController.configs = ["channelName":channelName]
        self.navigationController?.pushViewController(newViewController, animated: true)
    }
}

class ScreenShareMain: BaseViewController {
    var localVideo = Bundle.loadVideoView(type: .local, audioOnly: false)
    var remoteVideo = Bundle.loadVideoView(type: .remote, audioOnly: false)
    
    @IBOutlet weak var container: AGEVideoContainer!
    @IBOutlet weak var broadcasterPickerContainer: UIView!
    var agoraKit: AgoraRtcEngineKit!
    private lazy var screenParams: AgoraScreenCaptureParameters2 = {
        let params = AgoraScreenCaptureParameters2()
        params.captureVideo = true
        params.captureAudio = true
        let audioParams = AgoraScreenAudioParameters()
        audioParams.captureSignalVolume = 50
        params.audioParams = audioParams
        let videoParams = AgoraScreenVideoParameters()
        videoParams.dimensions = screenShareVideoDimension()
        videoParams.frameRate = .fps30
        videoParams.bitrate = AgoraVideoBitrateStandard
        params.videoParams = videoParams
        return params
    }()
    
    private lazy var option: AgoraRtcChannelMediaOptions = {
        let option = AgoraRtcChannelMediaOptions()
        option.clientRoleType = GlobalSettings.shared.getUserRole()
        option.publishCameraTrack = true
        option.publishMicrophoneTrack = true
        option.autoSubscribeAudio = true
        option.autoSubscribeVideo = true
        return option
    }()
    private lazy var screenOption: AgoraRtcChannelMediaOptions = {
        let option = AgoraRtcChannelMediaOptions()
        option.clientRoleType = .broadcaster
        option.publishCameraTrack = false
        option.publishMicrophoneTrack = false
        option.publishScreenCaptureAudio = true
        option.publishScreenCaptureVideo = true
        return option
    }()
    
    private let screenShareId = SCREEN_SHARE_UID
    private let screenShareBroadcasterId = SCREEN_SHARE_BROADCASTER_UID
    private var systemBroadcastPicker: RPSystemBroadcastPickerView?
    
    // indicate if current instance has joined channel
    var isJoined: Bool = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // prepare system broadcaster picker
//        prepareSystemBroadcaster()
        
        // layout render view
        localVideo.setPlaceholder(text: "Local Host".localized)
        remoteVideo.setPlaceholder(text: "Remote Host".localized)
        container.layoutStream(views: [localVideo, remoteVideo])
        
        // set up agora instance when view loaded
        let config = AgoraRtcEngineConfig()
        config.appId = KeyCenter.AppId
        config.areaCode = GlobalSettings.shared.area
        config.channelProfile = .liveBroadcasting
        agoraKit = AgoraRtcEngineKit.sharedEngine(with: config, delegate: self)
        agoraKit.setLogFile(LogUtils.sdkLogPath())
        
        // make myself a broadcaster
        agoraKit.setClientRole(GlobalSettings.shared.getUserRole())
        
        // enable video module and set up video encoding configs
        agoraKit.enableVideo()
        agoraKit.enableAudio()
        agoraKit.setVideoEncoderConfiguration(AgoraVideoEncoderConfiguration(size: AgoraVideoDimension640x360,
                                                                             frameRate: .fps30,
                                                                             bitrate: AgoraVideoBitrateStandard,
                                                                             orientationMode: .adaptative, mirrorMode: .auto))
        // set up local video to render your local camera preview
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.uid = 0
        // the view to be binded
        videoCanvas.view = localVideo.videoView
        videoCanvas.renderMode = .hidden
        agoraKit.setupLocalVideo(videoCanvas)
        // you have to call startPreview to see local video
        agoraKit.startPreview()

        // Set audio route to speaker
        agoraKit.setDefaultAudioRouteToSpeakerphone(true)
        // start joining channel
        // 1. Users can only see each other after they join the
        // same channel successfully using the same app id.
        // 2. If app certificate is turned on at dashboard, token is needed
        // when joining channel. The channel name and uid used to calculate
        // the token has to match the ones used for channel join
        
        joinChannel(uid: screenShareId, option: option)
    }
    
    private func joinChannel(uid: UInt, option: AgoraRtcChannelMediaOptions) {
        guard let channelName = configs["channelName"] as? String else {return}
        let connection = AgoraRtcConnection()
        connection.channelId = channelName
        connection.localUid = uid
        NetworkManager.shared.generateToken(channelName: channelName, uid: uid) { token in
            let result = self.agoraKit.joinChannelEx(byToken: token, connection: connection, delegate: self, mediaOptions: option, joinSuccess: nil)
            if result != 0 {
                // Usually happens with invalid parameters
                // Error code description can be found at:
                // en: https://docs.agora.io/en/Voice/API%20Reference/oc/Constants/AgoraErrorCode.html
                // cn: https://docs.agora.io/cn/Voice/API%20Reference/oc/Constants/AgoraErrorCode.html
                self.showAlert(title: "Error", message: "joinChannel call failed: \(result), please check your params")
            }
        }
    }
    
    func prepareSystemBroadcaster() {
        if #available(iOS 12.0, *) {
            let frame = CGRect(x: 0, y:0, width: 60, height: 60)
            systemBroadcastPicker = RPSystemBroadcastPickerView(frame: frame)
            systemBroadcastPicker?.showsMicrophoneButton = false
            systemBroadcastPicker?.autoresizingMask = [.flexibleTopMargin, .flexibleRightMargin]
            let bundleId = Bundle.main.bundleIdentifier ?? ""
            systemBroadcastPicker?.preferredExtension = "\(bundleId).Agora-ScreenShare-Extension";
            
        } else {
            self.showAlert(message: "Minimum support iOS version is 12.0")
        }
    }
    
    private func screenShareVideoDimension() -> CGSize {
        let screenSize = UIScreen.main.bounds
        var boundingSize = CGSize(width: 540, height: 960)
        let mW: CGFloat = boundingSize.width / screenSize.width
        let mH: CGFloat = boundingSize.height / screenSize.height
        if mH < mW {
            boundingSize.width = boundingSize.height / screenSize.height * screenSize.width
        } else if mW < mH {
            boundingSize.height = boundingSize.width / screenSize.width * screenSize.height
        }
        return boundingSize
    }
    
    @IBAction func clickCaptureAudio(_ sender: UISwitch) {
        screenParams.captureAudio = sender.isOn
    }
    @IBAction func clickCaptureVideo(_ sender: UISwitch) {
        screenParams.captureVideo = sender.isOn
    }
    @IBAction func captureSignalVolumeSlider(_ sender: UISlider) {
        screenParams.audioParams.captureSignalVolume = Int(sender.value * 100)
    }
    
    func isScreenShareUid(uid: UInt) -> Bool {
        return uid >= SCREEN_SHARE_UID_MIN && uid <= SCREEN_SHARE_UID_MAX
    }
    
    override func willMove(toParent parent: UIViewController?) {
        if parent == nil {
            // leave channel when exiting the view
            if isJoined {
                guard let channelName = configs["channelName"] as? String else {return}
                agoraKit.disableAudio()
                agoraKit.disableVideo()
                let connection = AgoraRtcConnection()
                connection.localUid = screenShareId
                connection.channelId = channelName
                agoraKit.leaveChannelEx(connection) { stats in
                    LogUtils.log(message: "left channel, duration: \(stats.duration)", level: .info)
                }
                let screenConnection = AgoraRtcConnection()
                screenConnection.localUid = UInt(screenShareBroadcasterId)
                screenConnection.channelId = channelName
                agoraKit.leaveChannelEx(screenConnection) { stats in
                    LogUtils.log(message: "left channel, duration: \(stats.duration)", level: .info)
                }
                AgoraRtcEngineKit.destroy()
            }
        }
    }
    @IBAction func stopScreenCapture(_ sender: Any) {
        guard let channelName = configs["channelName"] as? String else {return}
        agoraKit.stopScreenCapture()
        screenOption.publishCustomVideoTrack = false
        agoraKit.updateChannel(with: screenOption)
        let screenConnection = AgoraRtcConnection()
        screenConnection.localUid = UInt(screenShareBroadcasterId)
        screenConnection.channelId = channelName
        agoraKit.leaveChannelEx(screenConnection) { stats in
            LogUtils.log(message: "left channel, duration: \(stats.duration)", level: .info)
        }
    }
    @IBAction func startScreenCapture(_ sender: Any) {
        prepareSystemBroadcaster()
        agoraKit.startScreenCapture(screenParams)
        
        guard let picker = systemBroadcastPicker else { return }
        for view in picker.subviews where view is UIButton {
            (view as? UIButton)?.sendActions(for: .allEvents)
            break
        }
        joinChannel(uid: UInt(screenShareBroadcasterId), option: screenOption)
    }
    @IBAction func updateScreenCapture(_ sender: Any) {
        
        agoraKit.updateScreenCapture(screenParams)
    }
    
}

/// agora rtc engine delegate events
extension ScreenShareMain: AgoraRtcEngineDelegate {
    /// callback when warning occured for agora sdk, warning can usually be ignored, still it's nice to check out
    /// what is happening
    /// Warning code description can be found at:
    /// en: https://docs.agora.io/en/Voice/API%20Reference/oc/Constants/AgoraWarningCode.html
    /// cn: https://docs.agora.io/cn/Voice/API%20Reference/oc/Constants/AgoraWarningCode.html
    /// @param warningCode warning code of the problem
    func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurWarning warningCode: AgoraWarningCode) {
        LogUtils.log(message: "warning: \(warningCode.description)", level: .warning)
    }
    
    /// callback when error occured for agora sdk, you are recommended to display the error descriptions on demand
    /// to let user know something wrong is happening
    /// Error code description can be found at:
    /// en: https://docs.agora.io/en/Voice/API%20Reference/oc/Constants/AgoraErrorCode.html
    /// cn: https://docs.agora.io/cn/Voice/API%20Reference/oc/Constants/AgoraErrorCode.html
    /// @param errorCode error code of the problem
    func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurError errorCode: AgoraErrorCode) {
        LogUtils.log(message: "error: \(errorCode)", level: .error)
        self.showAlert(title: "Error", message: "Error \(errorCode.description) occur")
    }
    
    /// callback when the local user joins a specified channel.
    /// @param channel
    /// @param uid uid of local user
    /// @param elapsed time elapse since current sdk instance join the channel in ms
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        self.isJoined = true
        LogUtils.log(message: "Join \(channel) with uid \(uid) elapsed \(elapsed)ms", level: .info)
    }
    
    /// callback when a remote user is joinning the channel, note audience in live broadcast mode will NOT trigger this event
    /// @param uid uid of remote joined user
    /// @param elapsed time elapse since current sdk instance join the channel in ms
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        LogUtils.log(message: "remote user join: \(uid) \(elapsed)ms", level: .info)
        
        if isScreenShareUid(uid: uid) {
            LogUtils.log(message: "Ignore screen share uid", level: .info)
            return
        }
        
        // Only one remote video view is available for this
        // tutorial. Here we check if there exists a surface
        // view tagged as this uid.
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.uid = uid
        // the view to be binded
        videoCanvas.view = remoteVideo.videoView
        videoCanvas.renderMode = .hidden
        agoraKit.setupRemoteVideo(videoCanvas)
    }
    
    /// callback when a remote user is leaving the channel, note audience in live broadcast mode will NOT trigger this event
    /// @param uid uid of remote joined user
    /// @param reason reason why this user left, note this event may be triggered when the remote user
    /// become an audience in live broadcasting profile
    func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        LogUtils.log(message: "remote user left: \(uid) reason \(reason)", level: .info)
        
        // to unlink your view from sdk, so that your view reference will be released
        // note the video will stay at its last frame, to completely remove it
        // you will need to remove the EAGL sublayer from your binded view
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.uid = uid
        // the view to be binded
        videoCanvas.view = nil
        videoCanvas.renderMode = .hidden
        agoraKit.setupRemoteVideo(videoCanvas)
    }
    
    /// Reports the statistics of the current call. The SDK triggers this callback once every two seconds after the user joins the channel.
    /// @param stats stats struct
    func rtcEngine(_ engine: AgoraRtcEngineKit, reportRtcStats stats: AgoraChannelStats) {
        localVideo.statsInfo?.updateChannelStats(stats)
    }
    
    /// Reports the statistics of the uploading local audio streams once every two seconds.
    /// @param stats stats struct
    func rtcEngine(_ engine: AgoraRtcEngineKit, localAudioStats stats: AgoraRtcLocalAudioStats) {
        localVideo.statsInfo?.updateLocalAudioStats(stats)
    }
    
    /// Reports the statistics of the video stream from each remote user/host.
    /// @param stats stats struct
    func rtcEngine(_ engine: AgoraRtcEngineKit, remoteVideoStats stats: AgoraRtcRemoteVideoStats) {
        remoteVideo.statsInfo?.updateVideoStats(stats)
    }
    
    /// Reports the statistics of the audio stream from each remote user/host.
    /// @param stats stats struct for current call statistics
    func rtcEngine(_ engine: AgoraRtcEngineKit, remoteAudioStats stats: AgoraRtcRemoteAudioStats) {
        remoteVideo.statsInfo?.updateAudioStats(stats)
    }
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, localVideoStateChangedOf state: AgoraVideoLocalState, error: AgoraLocalVideoStreamError, sourceType: AgoraVideoSourceType) {
        
        print("state == \(state)  error == \(error)")
        switch (sourceType, state) {
        case (.screen, .capturing):
            print("屏幕共享开始")

        case (.screen, .stopped):
            print("屏幕共享停止")

        case (.screen, .failed):
            print("断开连接")
        
        default: break
        }
    }
}
