package ca.concordia.filesystem.datastructures;

import java.nio.ByteBuffer;

public class FNode {
    private int blockIndex;
    private int next;

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.next = -1;
    }

    // Convert FNode to bytes for disk storage
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(8); // 4 + 4 = 8 bytes
        buffer.putInt(blockIndex);
        buffer.putInt(next);
        return buffer.array();
    }

    // Convert bytes from disk back to FNode object
    public static FNode fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int blockIndex = buffer.getInt();
        int next = buffer.getInt();
        
        FNode node = new FNode(blockIndex);
        node.setNext(next);
        return node;
    }

    // Getters and setters
    public int getBlockIndex() { return blockIndex; }
    public void setBlockIndex(int blockIndex) { this.blockIndex = blockIndex; }
    public int getNext() { return next; }
    public void setNext(int next) { this.next = next; }

   
}