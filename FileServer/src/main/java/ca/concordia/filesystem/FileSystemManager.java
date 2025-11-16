package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private RandomAccessFile disk;

    private static final int BLOCK_SIZE = 128;

    private FEntry[] inodeTable;
    private FNode[] fNodes;
    private boolean[] freeBlockList;

    // Layout calculations
    private final int FENTRY_SIZE = 15;
    private final int FNODE_SIZE = 8;
    private final int METADATA_SIZE;
    private final int DATA_START_OFFSET;
    private final int FENTRIES_START = 0;
    private final int FNODES_START;

    // READERS-WRITER LOCK for proper synchronization
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public FileSystemManager(String filename, int totalSize) {
        // Calculate disk layout
        METADATA_SIZE = (MAXFILES * FENTRY_SIZE) + (MAXBLOCKS * FNODE_SIZE);
        FNODES_START = MAXFILES * FENTRY_SIZE;
        DATA_START_OFFSET = METADATA_SIZE;

         File f = new File(filename);
        boolean existed = f.exists();
        long oldSize = existed ? f.length() : 0;

        try {
            // Now open the file
            this.disk = new RandomAccessFile(filename, "rw");

            // recreate if wrong size
            if (existed && oldSize != totalSize && oldSize > 0) {
                disk.close();
                f.delete();
                existed = false;
                this.disk = new RandomAccessFile(filename, "rw");
                disk.setLength(totalSize);
            }
            // Initialize in-memory arrays
            this.inodeTable = new FEntry[MAXFILES];
            this.fNodes = new FNode[MAXBLOCKS]; // NEW
            this.freeBlockList = new boolean[MAXBLOCKS];

            if (!existed || oldSize == 0) {
                disk.setLength(totalSize);
                System.out.println("Creating new file system...");
                initializeNewFileSystem();
            } else {
                if (disk.length() < METADATA_SIZE) {
                    throw new IllegalStateException("Existing disk too small.");
                }
                System.out.println("Loading existing file system...");
                loadExistingFileSystem();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FileSystemManager", e);
        }
    }

    private void initializeNewFileSystem() throws Exception {
        // Initialize all FEntries as empty
        for (int i = 0; i < MAXFILES; i++) {
            FEntry empty = new FEntry("", (short)0, (short)-1);
            inodeTable[i] = empty;

            disk.seek(FENTRIES_START + i * FENTRY_SIZE);
            disk.write(empty.toBytes());
        }

        // Initialize all FNodes as free 
        for (int i = 0; i < MAXBLOCKS; i++) {
            FNode node = new FNode(-(i + 1));
            node.setNext(-1);
            fNodes[i] = node;
            freeBlockList[i] = true;

            disk.seek(FNODES_START + i * FNODE_SIZE);
            disk.write(node.toBytes());
        }
    }

    private void loadExistingFileSystem() throws Exception {
    // Load FEntries from disk
        for (int i = 0; i < MAXFILES; i++) {
            disk.seek(FENTRIES_START + i * FENTRY_SIZE);
            byte[] entry = new byte[FENTRY_SIZE];
            disk.readFully(entry);
            inodeTable[i] = FEntry.fromBytes(entry);
        }

    // Load FNodes from disk and build free block list
        for (int i = 0; i < MAXBLOCKS; i++) {
            disk.seek(FNODES_START + i * FNODE_SIZE);
            byte[] buff = new byte[FNODE_SIZE];
            disk.readFully(buff);
            fNodes[i] = FNode.fromBytes(buff);

        // Block is free if blockIndex is negative
            freeBlockList[i] = (fNodes[i].getBlockIndex() < 0);
        }
    }

    public String[] listFiles() {
        rwLock.readLock().lock();
        try {
            int count = 0;
            for (FEntry e : inodeTable)
                if (e != null && !e.getFilename().isEmpty()) count++;

            String[] out = new String[count];
            int idx = 0;
            for (FEntry e : inodeTable)
                if (e != null && !e.getFilename().isEmpty())
                    out[idx++] = e.getFilename();

            return out;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void createFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
        try {
            if (fileName.length() > 11)
                throw new IllegalArgumentException("ERROR: filename too long");

            // FIXED: Throw exception instead of returning
            for (FEntry e : inodeTable)
                if (e != null && fileName.equals(e.getFilename())) 
                    throw new Exception("ERROR: file '" + fileName + "' already exists");

            int freeSlot = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] == null || inodeTable[i].getFilename().isEmpty()) {
                    freeSlot = i;
                    break;
                }
            }

            if (freeSlot == -1) throw new Exception("ERROR: no free file slots available");

            // Create new FEntry
            FEntry newEntry = new FEntry(fileName, (short)0, (short)-1);
            inodeTable[freeSlot] = newEntry;

            // Write to disk
            disk.seek(FENTRIES_START + (freeSlot * FENTRY_SIZE));
            disk.write(newEntry.toBytes());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

     // find file by name in table
    private int findSlotForFile(String name) {
        for (int i = 0; i < MAXFILES; i++)
            if (inodeTable[i] != null && name.equals(inodeTable[i].getFilename()))
                return i;
        return -1;
    }

    // get a free data block
    private int grabFreeBlock() throws Exception {
        for (int i = 0; i < MAXBLOCKS; i++) {
            if (freeBlockList[i]) {
                freeBlockList[i] = false;

                // update node in memory
                FNode n = new FNode(i);
                n.setNext(-1);
                fNodes[i] = n;

                // and on disk
                disk.seek(FNODES_START + i * FNODE_SIZE);
                disk.write(n.toBytes());

                return i;
            }
        }
        throw new Exception("no more blocks");
    }

    // put block back in free list
    private void giveBackBlock(int idx) throws Exception {
        if (idx < 0 || idx >= MAXBLOCKS) return;

        freeBlockList[idx] = true;
        FNode n = new FNode(-(idx + 1));
        n.setNext(-1);
        fNodes[idx] = n;
        disk.seek(FNODES_START + idx * FNODE_SIZE);
        disk.write(n.toBytes());
    }

    // go through all linked blocks and free them
    private void dropBlockChain(short start) throws Exception {
        int curr = start;
        while (curr >= 0) {
            int next = fNodes[curr].getNext();
            giveBackBlock(curr);
            curr = next;
        }
    }

    // write new content 
    public void writeFile(String name, byte[] data) throws Exception {
        rwLock.writeLock().lock();
        try {
            int slot = findSlotForFile(name);
            if (slot == -1) {
                // if file doesn't exist, just create it
                createFile(name);
                slot = findSlotForFile(name);
            }

            FEntry entry = inodeTable[slot];

            if (entry.getFirstBlock() >= 0)
                dropBlockChain(entry.getFirstBlock());

            // handle null data
            if (data == null || data.length == 0) {
                entry.setFilesize((short)0);
                entry.setFirstBlock((short)-1);

                disk.seek(FENTRIES_START + slot * FENTRY_SIZE);
                disk.write(entry.toBytes());
                return;
            }

            int left = data.length;
            int pos = 0;
            int first = -1;
            int prev = -1;

            while (left > 0) {
                int blk = grabFreeBlock();
                if (first == -1) first = blk;

                if (prev != -1) {
                    fNodes[prev].setNext(blk);
                    disk.seek(FNODES_START + prev * FNODE_SIZE);
                    disk.write(fNodes[prev].toBytes());
                }

                int chunk = Math.min(left, BLOCK_SIZE);
                byte[] buff = new byte[BLOCK_SIZE];
                System.arraycopy(data, pos, buff, 0, chunk);

                disk.seek(DATA_START_OFFSET + blk * BLOCK_SIZE);
                disk.write(buff);

                left -= chunk;
                pos += chunk;
                prev = blk;
            }

            entry.setFirstBlock((short)first);
            entry.setFilesize((short)data.length);

            disk.seek(FENTRIES_START + slot * FENTRY_SIZE);
            disk.write(entry.toBytes());

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // load all bytes of a file
    public byte[] readFile(String name) throws Exception {
        rwLock.readLock().lock();
        try {
            int slot = findSlotForFile(name);
            if (slot == -1)
                throw new IllegalArgumentException("ERROR: file '" + name + "' does not exist");

            FEntry entry = inodeTable[slot];
            int size = entry.getFilesize();

            if (size <= 0 || entry.getFirstBlock() < 0)
                return new byte[0];

            byte[] out = new byte[size];
            int copied = 0;
            int blk = entry.getFirstBlock();

            // follow the linked list of blocks
            while (blk >= 0 && copied < size) {
                disk.seek(DATA_START_OFFSET + blk * BLOCK_SIZE);
                byte[] buff = new byte[BLOCK_SIZE];
                disk.readFully(buff);

                int part = Math.min(size - copied, BLOCK_SIZE);
                System.arraycopy(buff, 0, out, copied, part);
                copied += part;

                blk = fNodes[blk].getNext();
            }

            return out;

        } finally {
            rwLock.readLock().unlock();
        }
    }

    // delete file and its blocks
    public void deleteFile(String name) throws Exception {
        rwLock.writeLock().lock();
        try {
            int slot = findSlotForFile(name);
            if (slot == -1) 
                throw new Exception("ERROR: file '" + name + "' does not exist");

            FEntry entry = inodeTable[slot];

            if (entry.getFirstBlock() >= 0)
                dropBlockChain(entry.getFirstBlock());

            FEntry empty = new FEntry("", (short)0, (short)-1);
            inodeTable[slot] = empty;

            disk.seek(FENTRIES_START + slot * FENTRY_SIZE);
            disk.write(empty.toBytes());

        } finally {
            rwLock.writeLock().unlock();
        }
    }
}