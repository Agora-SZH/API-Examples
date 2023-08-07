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
    
}


class SettingsWindow: NSWindow {

    @IBAction func setting(_ sender: NSButton) {
        
    }
}
