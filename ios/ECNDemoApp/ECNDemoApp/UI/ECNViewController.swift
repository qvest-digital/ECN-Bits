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

import UIKit
import ecn_bits_w


class ECNViewController: UIViewController {
        
    @IBOutlet weak var hostTextField: UITextField!
    @IBOutlet weak var portTextField: UITextField!
    @IBOutlet weak var sendPacketButton: UIButton!
    @IBOutlet weak var startChannelButton: UIButton!
    @IBOutlet weak var segmentedControl: UISegmentedControl!
    @IBOutlet weak var tableView: UITableView!
    
    var ip: String?
    var port: String?
    var udpClient: ECNUDPclient? = nil
    var selectedECN: Int = 0
    var ecnStats = ECNStats()
    var results : [String] = []
    var paramsWereUpdated: Bool = false
    
    /// MARK - UI Life Cycle

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    
    /// MARK - UI Helpers
    
    func setupUI() {
        self.title = "ECN-Client"
        hostTextField.delegate = self
        portTextField.delegate = self
        
        startChannelButton.isEnabled = false
        
        segmentedControl.setTitle("no ECN", forSegmentAt: 0)
        segmentedControl.setTitle("ECT(1)", forSegmentAt: 1)
        segmentedControl.setTitle("ECT(0)", forSegmentAt: 2)
        segmentedControl.setTitle("ECT CE", forSegmentAt: 3)
        
        self.tableView.register(UITableViewCell.self, forCellReuseIdentifier: "kCellReuseD")
        
// uncomment for easy testing
//        hostTextField.text = "192.168.0.232"
//        portTextField.text = "4568"
//        ip = "192.168.0.232"
//        port = "4568"
        paramsWereUpdated = true
    }
    
    func resetUI() {
        results = []
        ecnStats = ECNStats()
        tableView.reloadData()
    }
    
    
    /// MARK - UI method actions
    
    @IBAction func sendPacketAction(_ sender: Any) {
        updateClientIfNeeded()
        DispatchQueue.global(qos: .userInitiated).async {
            self.udpClient?.connectSock { result in
                switch result {
                case .success(_):
                    self.udpClient?.sendData(payload: "Hi From The iOS side!")
                    self.udpClient?.setupReceive(expectedLength: 512)
                case .failure(let error):
                    print("error\(error)")
                }
            }
        }
    }
    
    @IBAction func startChannelAction(_ sender: Any) {
        // to be implemented
    }
        
    @IBAction func segmentedControlValueChanged(_ sender: Any) {
        selectedECN = segmentedControl.selectedSegmentIndex
        udpClient?.updateECN(ecn: selectedECN)
    }
    
    
    /// MARK
    
    func updateClientIfNeeded() {
        startClientWithCurrentSettings()
    }
    
    func startClientWithCurrentSettings() {
        resetUI()
        if (paramsWereUpdated) {
            if let port = self.port, let host = self.ip {
                let portInt = (port as NSString).integerValue
                self.udpClient = ECNUDPclient(host: host, port: portInt, delegate: self)
                paramsWereUpdated = false
            }
        }
    }
    
    func updateDataWithTextField(textField:UITextField) {
        if (textField == hostTextField) {
            paramsWereUpdated =  (self.ip != textField.text)
            self.ip = textField.text
        } else if (textField == portTextField) {
            paramsWereUpdated =  (self.port != textField.text)
            self.port = textField.text
        }
    }
    
    func valueForECN(ecn: ECNBit) -> String {
        switch ecn {
        case .noECN:
            return "no ECN"
        case .ECT_0:
            return "ECT(0)"
        case .ECT_1:
            return "ECT(1)"
        case .ECN_CE:
            return "ECN CE"
        case .bits(value: let value):
            return "\(value)"
        case .INVALID:
            return "invalid"
        @unknown default:
            return "unknown"
        }
    }
}

extension ECNViewController : UDPClientDelegate {
    func didReceieveResponse(data: String, ecnResult: ECNBit?) {
        if let ecn_res = ecnResult {
            ecnStats.addECNRsult(result: ecn_res)
            let newData = "\(ecn_res)\n" + data
            results.append(newData)
        } else {
            results.append(data)
        }

        tableView.reloadData()
    }
    
    func didFinishTransmission() {
        results.append(ecnStats.getStats())
        tableView.reloadData()
    }
    
    func didReceiveSendError(error: String) {}
    func didReceiveRecvError(error: String) {}
    func didReceiveSendResponse() {}
}

extension ECNViewController : UITableViewDelegate {
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return 60 // make it nicer
    }
}

extension ECNViewController : UITableViewDataSource {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return results.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell:UITableViewCell = self.tableView.dequeueReusableCell(withIdentifier: "kCellReuseD") as UITableViewCell? ?? UITableViewCell()
        cell.textLabel?.numberOfLines = 4 // should be dynamic
        cell.textLabel?.font = UIFont.systemFont(ofSize: 14)
        cell.textLabel?.text = results[indexPath.item]
        return cell
    }
}

extension ECNViewController : UITextFieldDelegate {
    func textFieldDidEndEditing(_ textField: UITextField) {
        updateDataWithTextField(textField: textField)
    }
    
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        updateDataWithTextField(textField: textField)
        textField.resignFirstResponder()
        return true
    }
}

