package ca.concordia.filesystem.datastructures;


public class FNode {

    private int blockIndex;
    private int next;

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.next = -1;
    }

    // Getters
    public int getBlockIndex() {
        return blockIndex;
    }

    public int getNext() {
        return next;
    }

    // Setters
    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public void setNext(int next) {
        this.next = next;
    }

}
