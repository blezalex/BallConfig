package ride.ballconfig;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import proto.Protocol;


public class SerialComm {
    ProtoHandler handler;

    static final int HEADER_LEN = 4;
    static final int CRC_LEN = 2;

    public interface ProtoHandler {
        void OnGeneric(Protocol.ReplyId reply);
        void OnConfig(Protocol.Config cfg);
        void OnStats(Protocol.Stats stats);
        void OnDebug(byte[] data);
    }

    public SerialComm(ProtoHandler handler)
    {
        this.handler = handler;
    }

    private void DecodeMessage(byte[] msgData) throws ProtocolException, InvalidProtocolBufferException {
        int len = toUnsignedInt(msgData[1]);

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
                handler.OnConfig(Protocol.Config.parseFrom(payload));
                break;
            case Protocol.ReplyId.STATS_VALUE:
                handler.OnStats(Protocol.Stats.parseFrom(payload));;
                break;
            case Protocol.ReplyId.DEBUG_BUFFER_VALUE:
                handler.OnDebug(payload);
                break;
        }

        System.arraycopy(msgData, HEADER_LEN, msgData, 0, payloadLen);
    }

    long lastMsgTime = 0;
    int writePos = 0;
    byte[] msgBuffer = new byte[1024];

    static final int READ_TIMEOUT_MS = 1000;

    public void RunReader(InputStream inputStream) throws IOException {
        int bytes_read;
        while ((bytes_read = inputStream.read(msgBuffer, writePos, msgBuffer.length - writePos)) != -1) {
            try {
                long time = System.currentTimeMillis();
                if (time > lastMsgTime + READ_TIMEOUT_MS) {
                    writePos = 0;
                }
                lastMsgTime = time;

                if ((bytes_read + writePos) >= msgBuffer.length)
                    writePos = 0;

                writePos += bytes_read;
                if (writePos > 2) {
                    int len = toUnsignedInt(msgBuffer[1]);
                    if (len <= writePos) {
                        DecodeMessage(msgBuffer);
                        int bytesToCopy = writePos - len;
                        if (bytesToCopy > 0) {
                            System.arraycopy(msgBuffer, len, msgBuffer, 0, bytesToCopy);
                            writePos = bytesToCopy;
                        }
                        else {
                            writePos = 0;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                writePos = 0;
            }
        }
    }

    public static void sendMsg(OutputStream out, Protocol.RequestId id) throws IOException {
        sendMsg(out, id, new byte[0]);
    }

    public static void sendConfig(OutputStream out, Protocol.Config cfg) throws IOException {
        byte[] data = cfg.toByteArray();
        sendMsg(out, Protocol.RequestId.WRITE_CONFIG, data);
    }

    private static void sendMsg(OutputStream out, Protocol.RequestId id, byte[] data) throws IOException {
        byte[] msg = new byte[data.length + HEADER_LEN + CRC_LEN];
        msg[0] = (byte) id.getNumber();
        msg[1] = (byte) msg.length; // TODO: check if conversion is correct
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
