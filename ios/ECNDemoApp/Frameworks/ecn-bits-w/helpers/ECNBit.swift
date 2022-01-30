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

public enum ECNBit {
    case noECN
    case ECT_0
    case ECT_1
    case ECN_CE
    case INVALID
    case bits(value:UInt8)
}

extension ECNBit {
    init?(value: Int) {
        if (value == 512) { //0x00
            self = .noECN
        } else if (value == 513) { //0x01
            self = .ECT_0
        } else if (value == 514) { //0x02
            self = .ECT_1
        } else if (value == 515) { //0x03
            self = .ECN_CE
        }  else {
            self = .INVALID
        }
    }
}
