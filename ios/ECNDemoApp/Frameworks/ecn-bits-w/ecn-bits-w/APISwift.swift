//
//  APISwift.swift
//  ecn-bits-w
//
//  Created by Ovidiu Rotaru on 28.10.2021.
//

import Foundation

struct ECN_Statistics {
    var timestamp: Double
}

class UDPClient {
    
    var host:String
    var port:String

    init(host:String, port:String) {
        self.host = host
        self.port = port
        
        var server = Server()
        server.start()
    }
    
    func sendData(data:NSData) {
        
    }
    
    func receiveData(data:NSData) {
        
    }
}

