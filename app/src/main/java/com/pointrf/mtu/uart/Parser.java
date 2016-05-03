package com.pointrf.mtu.uart;

public class Parser {

    enum State {
        PATTERN, MAC, RSSI, LEN, DATA, CR, LF
    };

    public static final byte   PointRFCode  = (byte) 0xE0;

    // 8 = MAC + RSSI + LEN, 5 = offset of pkt
    static private final int startPkt = 8 + 5;

    static private final byte[] syncPattern = "OK+SCAN:".getBytes();
    static private final int MacLen = 6;

    private byte[] msgBuf = new byte[4096];
    private int msgIndex = 0;

    private int patternIndex = 0;
    private int macIndex = 0;
    private int msgLen = 0;
    private int msgLenIndex = 0;
    private byte msgRssi = 0;
    private byte[] msgMac = new byte[MacLen];

    private State state = State.PATTERN;

    private UartCallback callback = null;

    private int globalOffset = 0;

    public Parser(UartCallback callback) {

        this.callback = callback;
    }

    public void newData(byte[] data, int len) {

        int idx = 0;

        while (idx < len) {

            switch (state) {
                case PATTERN:
                    idx = matchPattern(data, idx);
                    break;

                case MAC:
                    idx = matchMac(data, idx);
                    break;

                case RSSI:
                    idx = matchRSSI(data, idx);
                    break;

                case LEN:
                    idx = matchLEN(data, idx);
                    break;

                case DATA:
                    idx = matchData(data, idx);
                    break;

                case CR:
                    idx = matchCR(data, idx);
                    break;

                case LF:
                    idx = matchLF(data, idx);
                    break;
            }

        }

        globalOffset += idx;

    }

    private int matchPattern(byte[] data, int idx) {

        int i;

        for (i = idx; i < data.length && patternIndex < syncPattern.length; i++) {

            if (data[i] == syncPattern[patternIndex]) {
                patternIndex += 1;
            } else {
                if (patternIndex > 0)
                    patternIndex = 0;
            }
        }

        // matched pattern
        if (patternIndex >= syncPattern.length ) {
            state = State.MAC;
            patternIndex = 0;
            macIndex = 0;
            msgIndex = 0;
        }

        return i;
    }

    private int matchMac(byte[] data, int idx) {

        int i;

        for (i = idx; i < data.length && macIndex < MacLen; i++) {
            msgBuf[msgIndex++] = data[i];
            msgMac[macIndex] = data[i];
            macIndex += 1;
        }

        // matched MAC
        if (macIndex >= MacLen) {
            state = State.RSSI;
            macIndex = 0;
        }


        return i;

    }

    private int matchRSSI(byte[] data, int idx) {

        if (idx < data.length) {
            msgBuf[msgIndex++] = data[idx];
            msgRssi = data[idx];
            state = State.LEN;
            return idx + 1;
        } else
            return idx;
    }

    private int matchLEN(byte[] data, int idx) {

        if (idx < data.length) {
            msgBuf[msgIndex++] = data[idx];
            msgLen = data[idx];
            state = State.DATA;
            msgLenIndex = 0;

            if ( msgLen <= 0 || msgLen > 100 ) {
                System.err.println("msgLen error " + msgLen + " offset " + globalOffset + " idx " + idx);
                state = State.PATTERN;
                macIndex = 0;
            }

            return idx + 1;

        } else
            return idx;
    }

    private int matchData(byte[] data, int idx) {

        int i;

        for (i = idx; i < data.length && msgLenIndex < msgLen; i++) {
            msgBuf[msgIndex++] = data[i];
            msgLenIndex += 1;
        }

        // matched Data
        if (msgLenIndex >= msgLen ) {
            state = State.CR;
            msgLenIndex = 0;
        }

        return i;

    }

    private int matchCR(byte[] data, int idx) {

        if (idx < data.length) {
            msgBuf[msgIndex++] = data[idx];
            state = State.LF;
            return idx + 1;
        } else
            return idx;
    }

    private int matchLF(byte[] data, int idx) {

        if (idx < data.length) {
            msgBuf[msgIndex++] = data[idx];

            newMsg();

            state = State.PATTERN;
            return idx + 1;
        } else
            return idx;
    }

    private void newMsg() {

        if ( callback == null )
            return;

        // add 10 for MAC + RSSI + LEN
        byte[] rawMsg = null;
        try {
            rawMsg = new byte[msgLen + 10];
        } catch (Exception e ) {

            e.printStackTrace();
            return;
        }

        System.arraycopy(msgBuf, 0, rawMsg, 0, 10 + msgLen);

        //System.err.println(arrayToStr(rawMsg, 0, rawMsg.length, true));

        if ((byte) (msgBuf[startPkt + 4] & 0xF0 ) != PointRFCode)
            return;

        String bleId = String.format("%02x%02x", msgBuf[startPkt+1], msgBuf[startPkt]);

        callback.bleMsg(bleId, msgRssi, msgMac, rawMsg);

    }

    static public String arrayToStr(byte[] msg, int start, int len, boolean space) {

        String ret = "";

        for (int i = start; i < start+len; i++ )
            ret += String.format("%02x" + (space ? " " : ""), msg[i]);

        return ret;
    }

}