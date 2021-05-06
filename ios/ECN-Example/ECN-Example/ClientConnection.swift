//
//  ClientConnection.swift
//  ECN-Example
//
//  Created by Michael Schmidt on 06.05.21.
//

import Foundation
import Network

@available(iOS 12.0, *)
class ClientConnection {
    
    let nwConnection: NWConnection
    let queue = DispatchQueue(label: "Client connection Q")
    var handler: (String) -> Void
 
    init(nwConnection: NWConnection, handler: @escaping (String) -> Void) {
        self.nwConnection = nwConnection
        self.handler = handler
    }
    
    var didStopCallback: ((Error?) -> Void)? = nil
    
    func start() {
        print("connection will start")
        nwConnection.stateUpdateHandler = stateDidChange(to:)
        setupReceive()
        nwConnection.start(queue: queue)
    }
    
    private func stateDidChange(to state: NWConnection.State) {
        switch state {
        case .waiting(let error):
            connectionDidFail(error: error)
        case .ready:
            print("Client connection ready")
        case .failed(let error):
            connectionDidFail(error: error)
        default:
            break
        }
    }
    
    private func setupReceive() {
        nwConnection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { (data, _, isComplete, error) in
            if let data = data, !data.isEmpty {
                let message = String(data: data, encoding: .utf8)
                let log = "connection did receive, data: \(data as NSData) string: \(message ?? "-" )"
                print(log)
                self.handler(log)
            }
            if isComplete {
                self.connectionDidEnd()
            } else if let error = error {
                self.connectionDidFail(error: error)
            } else {
                self.setupReceive()
            }
        }
    }
        
    func send(data: Data, ecn: Int) {
        let ipMetaData = NWProtocolIP.Metadata()
        switch (ecn) {
        case    0:
            ipMetaData.ecn = .nonECT
        case 1:
            ipMetaData.ecn = .ect0
        case 2:
            ipMetaData.ecn = .ect1
        case 3:
            ipMetaData.ecn = .ce
        default:
            ipMetaData.ecn = .nonECT
        }
        let context = NWConnection.ContentContext(identifier: "ECN", metadata: [ipMetaData])
        nwConnection.send(content: data, contentContext: context, completion: .contentProcessed( { error in
            if let error = error {
                self.connectionDidFail(error: error)
                return
            }
                print("connection did send, data: \(data as NSData)")
        }))
    }
    
    func stop() {
        print("connection will stop")
        stop(error: nil)
    }
    
    private func connectionDidFail(error: Error) {
        print("connection did fail, error: \(error)")
        self.stop(error: error)
    }
    
    private func connectionDidEnd() {
        print("connection did end")
        self.stop(error: nil)
    }
    
    private func stop(error: Error?) {
        self.nwConnection.stateUpdateHandler = nil
        self.nwConnection.cancel()
        if let didStopCallback = self.didStopCallback {
            self.didStopCallback = nil
            didStopCallback(error)
        }
    }
}
