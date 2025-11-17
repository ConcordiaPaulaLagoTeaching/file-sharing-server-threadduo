package ca.concordia.filesystem.datastructures;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class FEntry {
    private String filename;
    private short filesize;
    private short firstBlock;

    public FEntry(String filename, short filesize, short firstBlock) throws IllegalArgumentException {
        if (filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename;
        this.filesize = filesize;
        this.firstBlock = firstBlock;
    }

    // Convert FEntry to bytes for disk storage
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(15); // 11 + 2 + 2 = 15 bytes
        
        // Convert filename to bytes (max 11 bytes)
        byte[] nameBytes = filename.getBytes(StandardCharsets.US_ASCII);
        buffer.put(nameBytes);
        
        // Pad with zeros if filename is shorter than 11 bytes
        for (int i = nameBytes.length; i < 11; i++) {
            buffer.put((byte) 0);
        }
        
        // Add file size and first block
        buffer.putShort(filesize);
        buffer.putShort(firstBlock);
        
        return buffer.array();
    }

    // Convert bytes from disk back to FEntry object
    public static FEntry fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // Read filename (first 11 bytes)
        byte[] nameBytes = new byte[11];
        buffer.get(nameBytes);
        String filename = new String(nameBytes, StandardCharsets.US_ASCII).trim(); // trim removes padding zeros
        
        // Read file size and first block
        short filesize = buffer.getShort();
        short firstBlock = buffer.getShort();
        
        return new FEntry(filename, filesize, firstBlock);
    }

    // getters and setters
    public String getFilename() { return filename; }
    public void setFilename(String filename) { 
        if (filename.length() > 11) {
            throw new IllegalArgumentException("Filename cannot be longer than 11 characters.");
        }
        this.filename = filename; 
    }
    public short getFilesize() { return filesize; }
    public void setFilesize(short filesize) { this.filesize = filesize; }
    public short getFirstBlock() { return firstBlock; }
    public void setFirstBlock(short firstBlock) { this.firstBlock = firstBlock; }

   
}