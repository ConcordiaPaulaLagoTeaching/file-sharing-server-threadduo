package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128;

    private FEntry[] inodeTable;
    private FNode[] fNodes; // NEW: Array to track FNodes in memory
    private boolean[] freeBlockList;
    
    // Layout calculations
    private final int FENTRY_SIZE = 15;
    private final int FNODE_SIZE = 8;
    private final int METADATA_SIZE;
    private final int DATA_START_OFFSET;
    private final int FENTRIES_START = 0;
    private final int FNODES_START;

    public FileSystemManager(String filename, int totalSize) {
        // Calculate disk layout
        METADATA_SIZE = (MAXFILES * FENTRY_SIZE) + (MAXBLOCKS * FNODE_SIZE);
        FNODES_START = MAXFILES * FENTRY_SIZE; // FNodes start after FEntries
        DATA_START_OFFSET = METADATA_SIZE;
        
        try {
            this.disk = new RandomAccessFile(filename, "rw");
            // Validate requested total size is sufficient for metadata + data
            if (totalSize < (METADATA_SIZE + (MAXBLOCKS * BLOCK_SIZE))) {
                throw new IllegalArgumentException("totalSize too small for filesystem metadata and data blocks.");
            }

            // Initialize in-memory arrays
            this.inodeTable = new FEntry[MAXFILES];
            this.fNodes = new FNode[MAXBLOCKS]; // NEW
            this.freeBlockList = new boolean[MAXBLOCKS];
            
            // Check if file is new or existing
            if (disk.length() == 0) {
                // Make sure the file has the requested size
                disk.setLength(totalSize);
                System.out.println("Creating new file system...");
                initializeNewFileSystem();
            } else {
                if (disk.length() < METADATA_SIZE) {
                    throw new IllegalStateException("Existing disk file too small to contain filesystem metadata.");
                }
                System.out.println("Loading existing file system...");
                loadExistingFileSystem();
            }
            
            System.out.println("FileSystemManager initialized:");
            System.out.println("  MAXFILES: " + MAXFILES + ", MAXBLOCKS: " + MAXBLOCKS);
            System.out.println("  FEntries: 0 to " + (FNODES_START - 1));
            System.out.println("  FNodes: " + FNODES_START + " to " + (DATA_START_OFFSET - 1));
            System.out.println("  Data blocks: " + DATA_START_OFFSET + " onwards");
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize FileSystemManager", e);
        }
        // Set singleton instance (if desired)
        instance = this;
    }

    private void initializeNewFileSystem() throws Exception {
        // Initialize all FEntries as empty
        for (int i = 0; i < MAXFILES; i++) {
            FEntry emptyEntry = new FEntry("", (short)0, (short)-1);
            inodeTable[i] = emptyEntry;
            
            // Write empty FEntry to disk
            byte[] entryBytes = emptyEntry.toBytes();
            disk.seek(FENTRIES_START + (i * FENTRY_SIZE));
            disk.write(entryBytes);
        }
        
        // Initialize all FNodes as free (negative blockIndex)
        for (int i = 0; i < MAXBLOCKS; i++) {
            FNode freeNode = new FNode(-(i + 1)); // Negative = free block
            freeNode.setNext(-1);
            fNodes[i] = freeNode;
            freeBlockList[i] = true;
            
            // Write FNode to disk
            byte[] nodeBytes = freeNode.toBytes();
            disk.seek(FNODES_START + (i * FNODE_SIZE));
            disk.write(nodeBytes);
        }
        
        System.out.println("New file system created with " + MAXFILES + " file slots and " + MAXBLOCKS + " blocks");
    }

    private void loadExistingFileSystem() throws Exception {
    // Load FEntries from disk
    for (int i = 0; i < MAXFILES; i++) {
        disk.seek(FENTRIES_START + (i * FENTRY_SIZE));
        byte[] entryData = new byte[FENTRY_SIZE];
        disk.readFully(entryData);
        inodeTable[i] = FEntry.fromBytes(entryData);
    }
    
    // Load FNodes from disk and build free block list
    for (int i = 0; i < MAXBLOCKS; i++) {
        disk.seek(FNODES_START + (i * FNODE_SIZE));
        byte[] nodeData = new byte[FNODE_SIZE];
        disk.readFully(nodeData);
        fNodes[i] = FNode.fromBytes(nodeData);
        
        // Block is free if blockIndex is negative
        freeBlockList[i] = (fNodes[i].getBlockIndex() < 0);
    }
    
        System.out.println("Existing file system loaded");
    }   

   
    public void createFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    // TODO: Add readFile, writeFile and other required methods
}