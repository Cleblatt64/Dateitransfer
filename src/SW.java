import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

public class SW extends ARQAbst{

    public SW(Socket socket) {
        super(socket);
    }

    public SW(Socket socket, int sessionID)  {
        super(socket, sessionID); 
    }

    
    @Override
    public void closeConnection() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'closeConnection'");
    }

    @Override
    public boolean data_req(byte[] hlData, int hlSize, boolean lastTransmission) {

        byte[] data = generateDataPacket(hlData, hlSize);
        socket.sendPacket(data);
        logger.log(Level.CONFIG, "Client-SW: data send");

        byte[] pNrb = Arrays.copyOfRange(hlData, 2, 3);
        int pNr = TransUtil.bytesToInt(pNrb);

        return waitForAck(pNr);
    }

    @Override
    protected boolean waitForAck(int packetNr) {
        socket.setTimeout(1000);
        DatagramPacket ackPacket;
        try {
            ackPacket = socket.receivePacket();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        byte[] pNrb = Arrays.copyOfRange(ackPacket.getData(), 2, 3);
        int pNr = TransUtil.bytesToInt(pNrb);

        logger.log(Level.CONFIG, "Client-SW: " + pNr + " received");
        return pNr == packetNr;
    }

    @Override
    protected int getPacketNr(DatagramPacket packet) {
        byte[] pNrb = Arrays.copyOfRange(packet.getData(), 2, 3);
        return TransUtil.bytesToInt(pNrb);
    }

    @Override
    protected void getAckData(DatagramPacket packet) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAckData'");
    }

    @Override
    protected int getSessionID(DatagramPacket packet) {
        byte[] sIDb = Arrays.copyOfRange(packet.getData(), 0, 2);
        return TransUtil.bytesToInt(sIDb);
    }

    @Override
    protected byte[] generateDataPacket(byte[] sendData, int dataSize) {
        return sendData;
    }

//************************************* Server ******************************** */

    @Override
    public byte[] data_ind_req(int... values) throws TimeoutException {
        DatagramPacket dataPacket;

        dataPacket = socket.receivePacket();
        logger.log(Level.CONFIG, "Server-SW: Data received");

        sessionID = getSessionID(dataPacket);
        int pNr = getPacketNr(dataPacket);

        sendAck(pNr);
        return dataPacket.getData();
    }

    @Override
    byte[] generateAckPacket(int pNr) {

        byte[] data = new byte[4];
        System.arraycopy(TransUtil.numToBytes(sessionID),0,data,0,2);
        System.arraycopy(TransUtil.numToBytes((char) pNr),0,data,2,1);
        System.arraycopy(TransUtil.numToBytes((char) 1),0,data,3,1);

        return data;
    }

    @Override
    void sendAck(int nr) {
        socket.sendPacket(generateAckPacket(nr));
    }

    @Override
    boolean checkStart(DatagramPacket packet) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'checkStart'");
    }

}
