using System;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;

namespace ECNBits.Windows
{
    
    /// <summary>
    /// A client sample that connects to the sample ws2 server
    /// </summary>
    public class EcnClientSample : MonoBehaviour
    {
        /// <summary>
        /// The IP of the sample server
        /// </summary>
        public string host;
        /// <summary>
        /// The port of the sample server
        /// </summary>
        public int port;
        /// <summary>
        /// The message sent to the server on connect
        /// </summary>
        public string message;

        private readonly CancellationTokenSource tokenSource = new CancellationTokenSource();

        #region unity lifecyle
        
        private void Start()
        {
            var cancellationToken = tokenSource.Token;
            var currentHost = host;
            var currentPort = port;
            var augmentedMessage = $"{message}-from-{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}";
            Task.Run(() => SocketTask(currentHost, currentPort, augmentedMessage, cancellationToken));
        }

        private void OnDestroy()
        {
            tokenSource.Cancel();
            tokenSource.Dispose();
        }

        #endregion
        
        /// <summary>
        /// This task will connect to the specified server and will poll for incoming messages,
        /// each time logging the status of the ECN Bits
        /// </summary>
        public static async Task SocketTask(string host, int port, string message, CancellationToken token)
        {
            const uint BUFFER_SIZE = 512;
            
            // Make sure the task will be run on the ThreadPool and not on the Unity UI thread
            await Task.Yield();

            var buffer = IntPtr.Zero;
            try
            {
                // Create socket, Prep using the ECNBits Api and connect to the sample server
                var socket = new Socket(SocketType.Dgram, ProtocolType.Udp);
                Api.Prep(socket, (int)AddressFamily.InterNetwork);
                socket.Connect(host, port);
                Debug.Log($"Connected to server! LocalEndpoint<{socket.LocalEndPoint}>, RemoteEndpoint<{socket.RemoteEndPoint}>!");

                socket.ReceiveTimeout = 50;
                Debug.Log($"Modified socket timeout: {socket.ReceiveTimeout}!");

                // Send message to server
                var messageAsBytes = Encoding.ASCII.GetBytes(message);
                socket.Send(messageAsBytes, 0, messageAsBytes.Length, SocketFlags.None, out var errorCode);
                Debug.Log($"Sent message<{message}> to server...  ErrorCode:{errorCode}!");

                // Allocate a buffer of unmanaged memory 
                buffer = Marshal.AllocHGlobal((int)BUFFER_SIZE);
                ushort ecnResult = 0;

                while (true)
                {
                    // If cancellation was requested, break out of the while loop
                    token.ThrowIfCancellationRequested();

                    // Read data using the ECNBits Api
                    var readBits = Api.Receive(socket, buffer, BUFFER_SIZE, 0, ref ecnResult);
                    var isValid = Api.IsValid(ecnResult);

                    Debug.Log($"Read {readBits} bits. EcnResult:{ConvertToBitString(ecnResult, 16)}!");
                    Debug.Log($"EcnResult validity:{isValid}, shortname:{Api.GetDescription(ecnResult)}");

                    // If the read ECNBits are valid, log their state
                    if (isValid)
                    {
                        var ecnBits = Api.GetBits(ecnResult);
                        Debug.Log( $"ECNBits:<{ConvertToBitString(ecnBits, 2)}>, meaning:<{Constants.ECNBITS_MEANINGS[ecnBits]}>");
                    }
                }
            }
            catch (Exception e)
            {
                Debug.Log($"Received exception of type {e.GetType()} - with message <{e.Message}!>");
            }
            finally
            {
                // Free all allocated unmanaged memory
                Marshal.FreeHGlobal(buffer);
            }
        }

        public static string ConvertToBitString(ushort bits, uint pad) => Convert.ToString(bits, 2).PadLeft((int)pad, '0');
    }
}