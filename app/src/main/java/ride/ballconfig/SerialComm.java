package ride.ballconfig;

import com.google.protobuf.DynamicMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import proto.Protocol;


public class SerialComm {
    ProtoHandler handler;

    static final int HEADER_LEN = 4;
    static final int CRC_LEN = 2;

    static final int MAX_MSG_LEN = 2048;

    enum ConnectionState {
        WaitingForBtDevice,
        WaitingForResponse,
        WaitingToSync,
        GotDescriptor,
        GotConfig,
        Disconnected,
    }

    public interface ProtoHandler {
        void OnGeneric(Protocol.ReplyId reply);

        void OnConfig(byte[] cfg);

        void OnStats(Protocol.Stats stats);

        void OnDebug(byte[] data);

        void OnConfigDescriptor(byte[] data);

        void OnConnectionStateChange(ConnectionState new_state);
    }

    public SerialComm(ProtoHandler handler) {
        this.handler = handler;
    }

    private int GetMsgLength(byte[] msgData) {
        return toUnsignedInt(msgData[1]) | (toUnsignedInt(msgData[2]) << 8);
    }

    private void DecodeMessage(byte[] msgData) throws Exception {
        int len = GetMsgLength(msgData);
        if (len < HEADER_LEN + CRC_LEN || len > MAX_MSG_LEN) {
            throw new Exception("Bad length");
        }

        int crc = CRC.compute(msgData, 0, len - CRC_LEN);
        byte high = msgData[len - CRC_LEN + 1];
        byte low = msgData[len - CRC_LEN];

        byte calcLow = (byte) (crc & 0xFF);
        byte calcHigh = (byte) ((crc & 0xFF00) >> 8);

        if (high != calcHigh || low != calcLow) {
            throw new ProtocolException(Protocol.ReplyId.CRC_MISMATCH);
        }

        int payloadLen = len - HEADER_LEN - CRC_LEN;
        byte[] payload = null;
        if (payloadLen > 0) {
            payload = new byte[payloadLen];
            System.arraycopy(msgData, HEADER_LEN, payload, 0, payloadLen);
        }

        int msgId = msgData[0];
        switch (msgId) {
            case Protocol.ReplyId.GENERIC_OK_VALUE:
            case Protocol.ReplyId.GENERIC_FAIL_VALUE:
            case Protocol.ReplyId.CRC_MISMATCH_VALUE:
                handler.OnGeneric(Protocol.ReplyId.forNumber(msgId));
                break;
            case Protocol.ReplyId.CONFIG_VALUE:
                handler.OnConfig(payload);
                break;
            case Protocol.ReplyId.STATS_VALUE:
                handler.OnStats(Protocol.Stats.parseFrom(payload));
                break;
            case Protocol.ReplyId.DEBUG_BUFFER_VALUE:
                handler.OnDebug(payload);
                break;
            case Protocol.ReplyId.CONFIG_DESCRIPTOR_VALUE:
                handler.OnConfigDescriptor(payload);
                break;
        }

        System.arraycopy(msgData, HEADER_LEN, msgData, 0, payloadLen);
    }

    long lastMsgTime = 0;
    int writePos = 0;
    byte[] msgBuffer = new byte[1024*4];

    static final int READ_TIMEOUT_MS = 5000;

    private void discardBufferdByte() {
        // Outside of valid range of msg id, skip byte and try again
        System.arraycopy(msgBuffer, 1, msgBuffer, 0, writePos - 1);
        writePos--;
    }

    public void RunReader(InputStream inputStream) throws IOException {
        int bytes_read;
        while ((bytes_read = inputStream.read(msgBuffer, writePos, msgBuffer.length - writePos)) != -1) {

            long time = System.currentTimeMillis();
            if (time > lastMsgTime + READ_TIMEOUT_MS) {
                writePos = 0;
            }
            lastMsgTime = time;

            if ((bytes_read + writePos) >= msgBuffer.length)
                writePos = 0;

            writePos += bytes_read;
            while (true) {
                while (writePos > 0 && (msgBuffer[0] < 1 || msgBuffer[0] > Protocol.ReplyId.CONFIG_DESCRIPTOR_VALUE))
                {
                    discardBufferdByte();
                }

                if (writePos > 3 && msgBuffer[3] != 0) {
                    discardBufferdByte();
                    continue;
                }

                // Attempt to decode accumulated so far messages.
                if (writePos < 2) {
                    // the length is at pos 1 so at least 2 bytes required.
                    break;
                }


                int len = GetMsgLength(msgBuffer);
                if (len > writePos && len < MAX_MSG_LEN) {
                    // Only a part of the message is received so far, wait for more.

                    // TODO: Attempt to decode message at different offset in case length is too long?
                    break;
                }

                try {
                    DecodeMessage(msgBuffer);
                    // Successfully decoded one message, copy the leftover
                    int leftoverBytes = writePos - len;
                    if (leftoverBytes > 0) {
                        System.arraycopy(msgBuffer, len, msgBuffer, 0, leftoverBytes);
                    }
                    writePos = leftoverBytes;
                } catch (Exception e) {
                    // Failed to decode the message. Shift 1 byte and try again.
//                    e.printStackTrace();
                    discardBufferdByte();
                }
            }
        }
    }

    public static void sendMsg(OutputStream out, Protocol.RequestId id) throws IOException {
        sendMsg(out, id, new byte[0]);
    }

    public static void sendConfig(OutputStream out, byte[] cfg) throws IOException {
        sendMsg(out, Protocol.RequestId.WRITE_CONFIG, cfg);
    }

    public static void setDebugStreamId(OutputStream out, int id) throws IOException {
        byte[] data = new byte[1];
        data[0] = (byte) id;
        sendMsg(out, Protocol.RequestId.SET_DEBUG_STREAM_ID, data);
    }

    private static void sendMsg(OutputStream out, Protocol.RequestId id, byte[] data) throws IOException {
        if (data.length > MAX_MSG_LEN - HEADER_LEN + CRC_LEN) {
            throw new IOException("Message is too long! Max len is: " + (MAX_MSG_LEN - HEADER_LEN + CRC_LEN) + "actual: " + data.length);
        }

        byte[] msg = new byte[data.length + HEADER_LEN + CRC_LEN];
        msg[0] = (byte) id.getNumber();
        msg[1] = (byte) msg.length;
        msg[2] = (byte) (msg.length >> 8);
        System.arraycopy(data, 0, msg, HEADER_LEN, data.length);
        int crc = CRC.compute(msg, 0, msg.length - CRC_LEN);
        byte calcLow = (byte) (crc & 0xFF);
        byte calcHigh = (byte) ((crc & 0xFF00) >> 8);

        msg[msg.length - CRC_LEN + 1] = calcHigh;
        msg[msg.length - CRC_LEN] = calcLow;

        out.write(msg);
        out.flush();

        StringBuilder sb = new StringBuilder();
        for (byte b : msg) {
            sb.append(String.format("%02X ", b));
        }
        System.out.println(sb.toString());
    }

    private static int toUnsignedInt(byte b) {
        return ((int) b) & 0xff;
    }
}
