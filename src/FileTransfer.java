import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.*;
import java.util.zip.CRC32;

public class FileTransfer implements FT{
    
    private final ARQ myARQ;
    Logger logger;

    public String fileName;

    public FileTransfer(String host, Socket socket, String fileName, String arq) {
        myARQ = new SW(socket);
        this.fileName = fileName;
        logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        logger.log(Level.CONFIG, "FileTransfer: " + arq + " " + fileName + " " + host);
    }

    public FileTransfer(Socket socket, String dir) {
        myARQ = new SW(socket);
        logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        logger.log(Level.CONFIG, "Server-FT: " + dir);
    }

    short createSessionID() {
        Random r = new Random();
        return (short) r.nextInt(Short.MAX_VALUE + 1);
    }

    byte[] createStartData(short sID, long fileSize){
        byte[] data = new byte[18];
        System.arraycopy(TransUtil.numToBytes(sID),0,data,0,2);
        System.arraycopy(TransUtil.numToBytes((char) 0),0,data,2,1);
        System.arraycopy(("Start").getBytes(),0,data,3,5);
        System.arraycopy(TransUtil.numToBytes(fileSize),0,data,8,8);
        System.arraycopy(TransUtil.numToBytes(fileName.length()),0,data,16,2);

        data = TransUtil.appendBytes(data, fileName.getBytes());

        CRC32 crc32 = new CRC32();
        crc32.update(data);

        data = TransUtil.appendBytes(data, TransUtil.numToBytes(crc32.getValue()));

        return data;
    }

    byte[] createEndData(short sID, int pNr, CRC32 c){
        byte[] data = new byte[7];
        System.arraycopy(TransUtil.numToBytes(sID),0,data,0,2);
        System.arraycopy(TransUtil.numToBytes((char) pNr),0,data,2,1);
        System.arraycopy(("Stop").getBytes(),0,data,3,4);

        data = TransUtil.appendBytes(data, TransUtil.numToBytes(c.getValue()));

        return data;
    }

    byte[] createData(short sID, short pNr, byte[] pData){
        byte[] data = new byte[3];
        System.arraycopy(TransUtil.numToBytes(sID),0,data,0,2);
        System.arraycopy(TransUtil.numToBytes((char) pNr),0,data,2,1);

        data = TransUtil.appendBytes(data, pData);

        return data;
    }

    @Override
    public boolean file_req() throws IOException {
        short sessionID = createSessionID();
        File file = new File(fileName);
        long fileSize = file.length();
        byte[] startData = createStartData(sessionID, fileSize);
        logger.log(Level.CONFIG, "Client-FT: Start created");
        myARQ.data_req(startData, startData.length, false);
        logger.log(Level.CONFIG, "Client-FT: start send");

        byte[] fileData = Files.readAllBytes(file.toPath());
        byte[] pData;
        boolean ack = true;
        int lastPNr = 0;

        for (int i = 1; i<=(fileData.length/1400)+1; i++){

            if (i==(fileData.length/1400)+1) {
                pData = new byte[fileData.length % 1400];
                System.arraycopy(fileData, (i-1)*1400, pData, 0, (fileData.length % 1400));
            } else {
                pData = new byte[1400];
                System.arraycopy(fileData, (i-1)*1400, pData, 0, 1400);
            }

            logger.log(Level.CONFIG, "Client-FT: file size is: " + fileData.length);
            logger.log(Level.CONFIG, "Client-FT: packet size is: " + pData.length);

            pData = createData(sessionID, (short) i, pData);
            logger.log(Level.CONFIG, "Client-FT: Data Package "+ i +" created");

            ack = myARQ.data_req(pData, pData.length, ack);

            if (ack) {
                logger.log(Level.CONFIG, "Client-FT: Data Package "+ i +" successfully send");
            } else {
                logger.log(Level.CONFIG, "Client-FT: Data Package "+ i +" error; retry");
                i--;
            }
            lastPNr = i;
        }

        CRC32 c = new CRC32();
        c.update(fileData);

        byte[] endData = createEndData(sessionID, lastPNr+1, c);

        byte[] crc = Arrays.copyOfRange(endData, 7, endData.length);
        logger.log(Level.CONFIG, "Client-FT: CRC32 from Client: " + (long) TransUtil.bytesToInt(crc));

        logger.log(Level.CONFIG, "Client-FT: End created");
        myARQ.data_req(endData, endData.length, true);
        logger.log(Level.CONFIG, "Client-FT: End send");

        return true;
    }

    //******** Server **********

    byte[] getFileData(byte[] b){
        return Arrays.copyOfRange(b, 3, b.length);
    }

    @Override
    public boolean file_init() throws IOException {
        byte[] data;
        try {
            data = myARQ.data_ind_req();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        String marker = new String(data, 3,5);
        logger.log(Level.CONFIG, "Server-FT: " + marker + " received");

        byte[] fileSizeB = Arrays.copyOfRange(data, 8, 16);
        int fileSize = TransUtil.bytesToInt(fileSizeB);

        logger.log(Level.CONFIG, "Server-FT: file size is: " + fileSize);

        if (!marker.equals("Start")) return false;

        byte[] end;

        ArrayList<byte[]> dataList = new ArrayList<>();
        while (true){
            try {
                data = myARQ.data_ind_req();
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
            logger.log(Level.CONFIG, "Server-FT: Packet received");

            byte[] bytes = Arrays.copyOfRange(data, 3, 7);
            marker = new String(bytes, StandardCharsets.UTF_8);
            logger.log(Level.CONFIG, "Server-FT: marker: " + marker);

            if (marker.equals("Stop")) {
                end = data;
                break;
            }

            dataList.add(data);
        }

        byte[] fileData = new byte[]{};

        int i = 1;
        for (byte[] b : dataList ){
            b = Arrays.copyOfRange(b, 0, 14000);
            fileData = TransUtil.appendBytes(fileData, getFileData(b));
            logger.log(Level.CONFIG, "Server-FT: Packet added: " + i);
            i++;
        }

        CRC32 c = new CRC32();
        c.update(fileData);

        byte[] crc = Arrays.copyOfRange(end, 7, end.length);

        logger.log(Level.CONFIG, "Server-FT: CRC32 from Client: " + (long) TransUtil.bytesToInt(crc));
        logger.log(Level.CONFIG, "Server-FT: CRC32 from Server: " + c.getValue());

        if ((long) TransUtil.bytesToInt(crc) == c.getValue()) {
            logger.log(Level.CONFIG, "Server-FT: CRC32 is valid and equal");
        } else {
            logger.log(Level.CONFIG, "Server-FT: CRC32 is not valid");
        }

        File outFile = new File("out.md");
        outFile.createNewFile();
        Files.write(outFile.toPath(), fileData);

        return true;
    }
}
