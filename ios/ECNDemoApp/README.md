# ECN-CLient Demo app
Project created with Xcode 13

This app contains an example for
getting Explicit Congestion Notification (ECN) bits from IP
traffic, which is useful for L4S-aware applications. They are
available under liberal (Open Source) licences.

The app contains a framework : ecn-bits-w project embbeded in the ECNDemoApp project.
ecn-bits-w.framework,  contains the c sources and provides and the ECNUDPCLient (that can send and receive data)

*This is work in progress.

*HOW TO USE 

Start the server from the ../c/server on a port
Open the app fill in the host and port  and hit Send Packet button

*Make sure you hit the done button on keyboard when changing the host and port
