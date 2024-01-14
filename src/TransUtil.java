public class TransUtil {
    static byte[] appendBytes(byte[] first, byte[] second){
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    static byte[] numToBytes(char c){
        return new byte[] {
                (byte) c};
    }

    static byte[] numToBytes(short s){
        return new byte[] {
                (byte) (s >>> 8),
                (byte) s};
    }

    static byte[] numToBytes(int i){
        return new byte[] {
                (byte) (i >>> 24),
                (byte) (i >>> 16),
                (byte) (i >>> 8),
                (byte) i};
    }

    static byte[] numToBytes(long l){
        return new byte[] {
                (byte) (l >>> 56),
                (byte) (l >>> 48),
                (byte) (l >>> 40),
                (byte) (l >>> 32),
                (byte) (l >>> 24),
                (byte) (l >>> 16),
                (byte) (l >>> 8),
                (byte) l};
    }

    static int bytesToInt(byte[] bytes){

        int i = 0;
        for (byte b : bytes) {
            i = (i << 8) + (b & 0xFF);
        }
        return i;
    }
}
