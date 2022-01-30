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


public struct ECNStats {
    
    var noECN: Int = 0
    var ect0: Int = 0
    var ect1: Int = 0
    var ectCE: Int = 0
    var errors: Int = 0
    
    public init() {}
    
    public mutating func addECNRsult(result:ECNBit) {
        switch result {
        case .noECN:
            self.noECN += 1
        case .ECT_0:
            self.ect0 += 1
        case .ECT_1:
            self.ect1 += 1
        case .ECN_CE:
            self.ectCE += 1
        case .INVALID:
            self.errors += 1
        case .bits(value: _):
            self.errors +=  1
        }
    }
    
    public func packetCount() -> Int {
        return noECN + ect0 + ect1 + ectCE + errors
    }
    
    public func getStats() -> String {
        var result = "Success!\n"
        let percent: Double = (Double(self.ectCE) / Double(packetCount())) * 100.0
        result += "\(percent)% of \(packetCount()) packets were congested"
        return result
    }
}
