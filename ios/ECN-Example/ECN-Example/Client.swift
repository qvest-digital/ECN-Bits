//
//  Client.swift
//  ECN-Example
//
//  Created by Michael Schmidt on 06.05.21.
//


import Foundation
import Network

class Client {
    let connection: ClientConnection
    let host: NWEndpoint.Host
    let port: NWEndpoint.Port
    
    var started = false
    

    
    init(host: String, port: UInt16, handler: @escaping (String) -> Void ) {
        self.host = NWEndpoint.Host(host)
        self.port = NWEndpoint.Port(rawValue: port)!
        let nwConnection = NWConnection(host: self.host, port: self.port, using: .udp)
        connection = ClientConnection(nwConnection: nwConnection, handler: handler)
    }
    
    func start() {
        print("Client started \(host) \(port)")
        connection.didStopCallback = didStopCallback(error:)
        connection.start()
        started = true
    }
    
    func stop() {
        connection.stop()
    }
    
    func send(data: Data, ecn: Int) {
        connection.send(data: data, ecn: ecn)
    }
    
    func didStopCallback(error: Error?) {
        if error == nil {
            print ("Connection ended")
        } else {
            print ("")
        }
        started = false
    }
}


