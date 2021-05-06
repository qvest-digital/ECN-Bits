//
//  ContentView.swift
//  ECN-Example
//
//  Created by Michael Schmidt on 06.05.21.
//


import SwiftUI

struct ContentView: View {
    @State private var hostname: String = ""
    @State private var port: String = ""
    @State private var showDetails = false
    @State private var log: String = ""
    
    private var ecn = ["NonECT","ECN0","ECN1","CE"]
    @State private var selectedECN = 0
    
    var body: some View {

        VStack {
                        HStack(spacing: 10.0){
                            TextField("hostname:", text: $hostname)
                                .padding(5)
                                .frame(width: 130.0)
                                .autocapitalization(.none)
                            TextField("port:", text: $port)
                                .padding(5)
                                .frame(width: 100.0)
                        }.padding(.vertical, 10.0)
                        HStack(alignment: .top){
                            Picker(selection: $selectedECN, label: Text("Options"), content: {
                                ForEach(0..<ecn.count){
                                    Text(self.ecn[$0])
                                }
                            }).frame(width: 80, height:50, alignment: .center).clipped()
                            Button.init(action: {self.sendMessage()}, label: {
                                Text("Send Packet")
                                    .fontWeight(.bold)
                                    .frame(width: 70.0, height: 50)
                                    .background(Color.gray)
                                    .foregroundColor(Color.white)
                            })
                            Button.init(action: {print($selectedECN)}, label: {
                                Text("Start Channel")
                                    .fontWeight(.bold)
                                    .frame(width: 75.0, height: 50)
                                    .background(Color.gray)
                                    .foregroundColor(Color.white)
                            })
                            Button.init(action: {}, label: {
                                Text("Licences")
                                    .fontWeight(.bold)
                                    .multilineTextAlignment(.center)
                                    .frame(width:77.0, height: 50)
                                    .background(Color.gray)
                                    .foregroundColor(Color.white)
                            })
                        }.padding(.vertical)
                        HStack(spacing: 5.0){
                            Text (log)
                                .background(Color.init(UIColor.systemGray6)
                                )
                        }
                        .padding(.all, 10.0)
            }
    }
    
    func sendMessage() {
        log = "\(hostname) \(port) \(selectedECN)"
        let iPort = UInt16(port)!
        let client = Client(host: hostname, port: iPort, handler: addLog(msg:))
        client.start()
        let data = "from iOS".data(using: .utf8)!
        client.send(data: data, ecn: selectedECN)
    }
    
    func addLog(msg: String){
        log += "\n\(msg)"
    }
}
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            ContentView()
        }
    }
}

