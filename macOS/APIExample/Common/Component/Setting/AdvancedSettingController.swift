//
//  AdvancedSettingController.swift
//  APIExample
//
//  Created by hanxiaoqing on 2023/8/7.
//  Copyright © 2023 Agora Corp. All rights reserved.
//

import Cocoa

class AdvancedSettingController: NSWindowController {
    
    //  set nib name for the window identical to file name
    override var windowNibName: NSNib.Name! { // StatusWindow.xib is the file nam for the xib
        return NSNib.Name("AdvancedSettingController")
    }
    
    override init(window: NSWindow!) {
        super.init(window: window)
    }
    
    required init?(coder: (NSCoder?)) { // I had a warning here  Using '!' in this location is deprecated and will be removed in a future release; consider changing this to '?' instead - For NSCoder!
        super.init(coder: coder!)   // should check in case coder is nil ?
    }
    
    override func windowDidLoad() {
        super.windowDidLoad()

        // Implement this method to handle any initialization after your window controller's window has been loaded from its nib file.
    }
    
    func realWindow() -> SettingsWindow? {
        return self.window as? SettingsWindow
    }
    
}

protocol SettingActionDelegate {
    func updateSettingForkey(_ key: ShowSettingKey)
    func updateAudioSettingForkey(_ key: ShowAudioSettingKey)
}


class SettingsWindow: NSWindow {
    
    var onSetDelegate: SettingActionDelegate?
    
    @IBAction func openH265(_ sender: NSSwitch) {
        ShowSettingKey.H265.writeValue(sender.state == .on)
        onSetDelegate?.updateSettingForkey(.H265)
    }
    
    @IBAction func colorinhance(_ sender: NSSwitch) {
        ShowSettingKey.colorEnhance.writeValue(sender.state == .on)
        onSetDelegate?.updateSettingForkey(.colorEnhance)
    }
    
    @IBAction func darklightinhance(_ sender: NSSwitch) {
        ShowSettingKey.lowlightEnhance.writeValue(sender.state == .on)
        onSetDelegate?.updateSettingForkey(.lowlightEnhance)
    }
    
    @IBAction func videonoisereduction(_ sender: NSSwitch) {
        ShowSettingKey.videoDenoiser.writeValue(sender.state == .on)
        onSetDelegate?.updateSettingForkey(.videoDenoiser)
    }
    
    @IBAction func ratesaving(_ sender: NSSwitch) {
        ShowSettingKey.PVC.writeValue(sender.state == .on)
        onSetDelegate?.updateSettingForkey(.PVC)
    }
    
    @IBAction func dimensionsSet(_ sender: NSPopUpButton) {
        ShowSettingKey.videoEncodeSize.writeValue(sender.indexOfSelectedItem)
        onSetDelegate?.updateSettingForkey(.videoEncodeSize)
    }
   
    @IBAction func frameRate(_ sender: NSPopUpButton) {
        ShowSettingKey.FPS.writeValue(sender.indexOfSelectedItem)
        onSetDelegate?.updateSettingForkey(.FPS)
    }
    
    @IBAction func supperResolution(_ sender: NSSegmentedControl) {
        ShowSettingKey.SR.writeValue(sender.indexOfSelectedItem)
        onSetDelegate?.updateSettingForkey(.SR)
    }
    

    @IBAction func bitrate(_ sender: NSSlider) {
        ShowSettingKey.videoBitRate.writeValue(sender.floatValue)
        onSetDelegate?.updateSettingForkey(.videoBitRate)
    }
    
    @IBAction func ains_selected(_ sender: NSSegmentedControl) {
        let state = ShowAudioSettingKey.AINS_STATE(rawValue: sender.indexOfSelectedItem)!
        ShowAudioSettingKey.AINS(state: state).writeValue()
        onSetDelegate?.updateAudioSettingForkey(.AINS(state: state))
    }
    
    @IBAction func aec_selected(_ sender: NSSegmentedControl) {
        let aecIsOn = sender.indexOfSelectedItem == 1
        ShowAudioSettingKey.AEC(isOn: aecIsOn).writeValue()
        onSetDelegate?.updateAudioSettingForkey(.AEC(isOn: aecIsOn))
    }
    
    @IBAction func aec_level_selected(_ sender: NSSegmentedControl) {
        let filter_length = ShowAudioSettingKey.AEC_FILTER_LENGTH(rawValue: sender.indexOfSelectedItem)!
        ShowAudioSettingKey.AEC_LENGTH(length: filter_length).writeValue()
        onSetDelegate?.updateAudioSettingForkey(.AEC_LENGTH(length: filter_length))
    }
    
    @IBAction func aec_filter_selected(_ sender: NSSegmentedControl) {
        let filter_type = sender.indexOfSelectedItem
        ShowAudioSettingKey.AEC_FILTER_TYPE(type: filter_type).writeValue()
        onSetDelegate?.updateAudioSettingForkey(.AEC_FILTER_TYPE(type: filter_type))
    }
}
