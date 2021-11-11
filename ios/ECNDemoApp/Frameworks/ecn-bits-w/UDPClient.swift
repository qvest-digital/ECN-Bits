/*-
 * Copyright © 2021
 *      ovidiuurotaru <ovidiu.rotaru@endava.com>
 * Licensor: Deutsche Telekom
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 */

import Foundation

public enum ECNUDPClientError: Error {
    case connect
    case unknown
}

public class ECNUDPclient {
    
    private weak var delegate: UDPClientDelegate?
    
    private var socketfd: Int32 = -1
    private var addr: sockaddr_storage = sockaddr_storage()
    private var host: String
    private var port: Int
    private var ecn:  Int = 0 // noECN default value
     
    public init(host:String, port:Int, delegate: UDPClientDelegate) {
        self.host = host
        self.port = port
        self.delegate = delegate
        
        guard let addresses = try? addressesFor(host: host, port: port) else {
            print("host not found")
            return
        }
        if addresses.count != 1 {
            print("host ambiguous; using the first one")
        }
        let address = addresses[0]
        addr = address
        socketfd = socket(Int32(address.ss_family), SOCK_DGRAM, 0)
        
        guard socketfd >= 0 else {
            print("socket failed")
            return
        }
    }
    
    /**
     Initiate a connection to a host by calling the connect method on a socket
     If this fails send or receive calls will also fail
     
     - Parameters:
        completion:  A callback (with a result parameter of type Result) cand be success or failure with error type
     */
    public func connect(completion: (Result<Bool, ECNUDPClientError>) -> Void) {
        let connectResult = addr.withSockAddr { sa, saLen in
            return  ecn_bits_w.connect(socketfd, sa, saLen)
        }
        
        if (connectResult >= 0) {
            completion(.success(true))
        } else {
            completion(.failure(.connect))
        }
    }
    
    /**
     Updates the ecn value, this  will be sent to the server with every send call
     
     - Parameters:
        ecn:  The ecn value that will be sent to the server 0 (no ECN), 1 (ECT 0),  2 (ECT 1),  3 (ECT CE)
     */
    public func updateECN(ecn : Int) {
        self.ecn = ecn
    }
    
    /**
     Sends data to a host
     If this fails send or recv calls will also fail
     
     - Parameters:
        data:  The data that will be sent as Data
        completion:  A callback (with a result parameter of type Result) cand be success or failure with error type
     */
    public func sendData(payload: String) {
        let sendResult =  addr.withSockAddr { (sa, saLen) -> Int in
            let ai_family = Int32(sa.pointee.sa_family)
            let msghdr = UnsafeMutablePointer<msghdr>.allocate(capacity: 64)
           
            let intBuffer = UnsafeMutablePointer<Int>.allocate(capacity: 1)
            let unsafePointer = UnsafeMutablePointer(mutating: intBuffer)
                    
            let iovec = payload.withCString { cpayload -> UnsafeMutablePointer<iovec> in
                let iovec = UnsafeMutablePointer<iovec>.allocate(capacity: 1)
                iovec.pointee.iov_base = UnsafeMutableRawPointer.init(mutating: cpayload)
                return iovec
            }
        
            iovec.pointee.iov_len = payload.utf8.count
            msghdr.pointee.msg_iov = iovec
            msghdr.pointee.msg_iovlen = 1
            
            let ecnPrepResult = ecnbits_prep(socketfd, ai_family)
            guard ecnPrepResult >= 0 else {
                print("ecnbits_prep failed")
                return -1
            }
            
            let ecnbits_tc =  ecnbits_tc(socketfd, ai_family, UInt8(ecn))
            guard ecnbits_tc >= 0 else {
                print("ecnbits_tc failed")
                return -1
            }
            
            let cmsgbuf = ecnbits_mkcmsg(UnsafeMutableRawPointer?.none, unsafePointer, ai_family, UInt8(ecn))
            guard cmsgbuf != nil else {
                print("ecnbits_mkcmsg failed")
                return -1
            }
            
            msghdr.pointee.msg_control = cmsgbuf
            msghdr.pointee.msg_controllen = socklen_t(ECNBITS_CMSGBUFLEN)
            let result = sendmsg(socketfd, msghdr, 0)
            
            free(intBuffer)
            free(iovec)
            free(msghdr)
            
            return result
        }
      
        guard sendResult >= 0 else {
            let errString = String(utf8String: strerror(errno)) ?? "Unknown error code"
            let message = "Send error = \(errno) (\(errString))"
            
            print(message)
            close(socketfd)
            
            DispatchQueue.main.async {
                self.delegate?.didReceiveSendError(error: errString)
            }
            
            return
        }
        
        DispatchQueue.main.async {
            self.delegate?.didReceiveSendResponse()
        }
    }
    
    /**
     Waits to receive data
     If this fails send or recv calls will also fail, this method awaits 4 packets (should be changed but our test server works like this) then calls didFinishTransmission
     
     - Parameters:
        expectedLength:  The size of data you are expecting
     */
    public func setupReceive(expectedLength: Int) {
        var packetCount = 0
        while (packetCount < 4) {
            
            var buffer = [CChar](repeating: 0, count: expectedLength)
            let ecnresult = UnsafeMutablePointer<UInt16>.allocate(capacity: 1)

            let bytesRead = buffer.withUnsafeMutableBytes { unsafeMutablePointer in
                return ecnbits_recv(self.socketfd, unsafeMutablePointer.baseAddress!, unsafeMutablePointer.count, O_NONBLOCK, ecnresult)
            }

            if bytesRead == -1 {
                let errString = String(utf8String: strerror(errno)) ?? "Unknown error code"
                let message = "Recv error = \(errno) (\(errString))"
                print(message)
                DispatchQueue.main.async {
                    self.delegate?.didReceiveRecvError(error: errString)
                }
                break;
            }

            let stringFromCChar = String(cString: buffer, encoding: .utf8)
            if let responseString = stringFromCChar {
                print(responseString)
                let result = Int("\(ecnresult.pointee)")
                DispatchQueue.main.async {
                    self.delegate?.didReceieveResponse(data: responseString, ecnResult: ECNBit(value:result!))
                }
            } else {
                DispatchQueue.main.async {
                    self.delegate?.didReceiveRecvError(error: "fatal can't be converted to string")
                }
            }
    
            free(ecnresult)
            packetCount += 1
        }
        
        DispatchQueue.main.async {
            self.delegate?.didFinishTransmission()
        }
    }
        
    /**
     Provides sockaddr_storage for host and port, we get the adress of the host
     
     - Parameters:
        host:  host data as String
        port:  port value as Int
     */
    func addressesFor(host: String, port: Int) throws -> [sockaddr_storage] {
        var hints = addrinfo()
        hints.ai_socktype = SOCK_DGRAM
        var addrList: UnsafeMutablePointer<addrinfo>? = nil
        let err = getaddrinfo(host, "\(port)", &hints, &addrList)
        guard err == 0, let start = addrList else {
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorCannotFindHost, userInfo: nil)
        }
        defer { free(addrList) }
        return sequence(first: start, next: { $0.pointee.ai_next} ).map { (addr) -> sockaddr_storage in
            sockaddr_storage(sa: addr.pointee.ai_addr, saLen: addr.pointee.ai_addrlen)
        }
    }
}
