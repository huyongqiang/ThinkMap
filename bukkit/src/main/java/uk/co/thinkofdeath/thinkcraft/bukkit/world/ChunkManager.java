/*
 * Copyright 2014 Matthew Collins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.co.thinkofdeath.thinkcraft.bukkit.world;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import uk.co.thinkofdeath.thinkcraft.bukkit.ThinkMapPlugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPOutputStream;

public class ChunkManager {

    private final ThinkMapPlugin plugin;
    private final World world;
    private final TLongSet activeChunks = new TLongHashSet();
    private final ReadWriteLock worldLock = new ReentrantReadWriteLock();

    public ChunkManager(ThinkMapPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | z & 0xFFFFFFFL;
    }

    public void activateChunk(Chunk chunk) {
        synchronized (activeChunks) {
            activeChunks.add(chunkKey(chunk.getX(), chunk.getZ()));
        }
    }

    public void deactivateChunk(Chunk chunk) {
        synchronized (activeChunks) {
            activeChunks.remove(chunkKey(chunk.getX(), chunk.getZ()));
        }
        final ChunkSnapshot snapshot = chunk.getChunkSnapshot();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

            @Override
            public void run() {
                try {
                    ByteBuf data = Unpooled.buffer();
                    gzipChunk(snapshot, data);
                    File worldFolder = new File(plugin.getWorldDir(), world.getName());
                    if (!worldFolder.exists() && !worldFolder.mkdirs()) {
                        throw new RuntimeException("Failed to create world folder");
                    }

                    // Lock the world for writing
                    Lock lock = worldLock.writeLock();
                    lock.lock();

                    try (RandomAccessFile region = new RandomAccessFile(new File(worldFolder,
                            String.format("region_%d-%d.dat", snapshot.getX() >> 5, snapshot.getZ() >> 5)
                    ), "rw")) {
                        if (region.length() < 4096 * 3) {
                            // Init header
                            region.seek(4096 * 3);
                            region.writeByte(0);
                        }
                        int id = ((snapshot.getX() & 0x1F) | ((snapshot.getZ() & 0x1F) << 5));
                        region.seek(8 * id);
                        int offset = region.readInt();
                        int size = region.readInt();
                        if (offset != 0) {
                            if (data.readableBytes() < ((size / 4096) + 1) * 4096) {
                                size = data.readableBytes();
                                region.seek(8 * id);
                                region.writeInt(offset);
                                region.writeInt(size);
                                region.seek(offset * 4096);
                                region.write(data.array());
                                return;
                            }
                        }
                        boolean[] usedSpace = new boolean[(int) ((region.length() / 4096) + 1)];
                        usedSpace[0] = usedSpace[1] = usedSpace[2] = true;
                        for (int i = 0; i < 32 * 32; i++) {
                            if (i == id) continue;
                            region.seek(8 * i);
                            int oo = region.readInt();
                            int os = region.readInt();
                            for (int j = oo; j < oo + ((os / 4096) + 1); j++) {
                                usedSpace[j] = true;
                            }
                        }
                        offset = usedSpace.length;
                        size = data.readableBytes();
                        search:
                        for (int i = 2; i < usedSpace.length; i++) {
                            if (!usedSpace[i]) {
                                for (int j = i + 1; j < i + ((size / 4096) + 1); j++) {
                                    if (j >= usedSpace.length || usedSpace[j]) {
                                        i += ((size / 4096) + 1);
                                        continue search;
                                    }
                                }
                                offset = i;
                                break;
                            }
                        }
                        region.seek(offset * 4096);
                        region.write(data.array());
                        region.seek(8 * id);
                        region.writeInt(offset);
                        region.writeInt(size);
                    } finally {
                        lock.unlock();
                        ReferenceCountUtil.release(data);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private ByteBuf getChunkData(final int x, final int z) {
        File worldFolder = new File(plugin.getWorldDir(), world.getName());
        Lock lock = worldLock.readLock();
        lock.lock();
        try (RandomAccessFile region = new RandomAccessFile(new File(worldFolder,
                String.format("region_%d-%d.dat", x >> 5, z >> 5)
        ), "r")) {
            if (region.length() < 4096 * 3) return null;
            int id = ((x & 0x1F) | ((z & 0x1F) << 5));
            region.seek(8 * id);
            int offset = region.readInt();
            int size = region.readInt();
            if (offset == 0) {
                return null;
            }
            region.seek(offset * 4096);
            byte[] data = new byte[size];
            region.readFully(data);
            return Unpooled.wrappedBuffer(data);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean getChunkBytes(final int x, final int z, ByteBuf out) {
        ChunkSnapshot chunk = null;
        boolean shouldGrabChunk;
        synchronized (activeChunks) {
            shouldGrabChunk = activeChunks.contains(chunkKey(x, z));
        }
        if (shouldGrabChunk) {
            try {
                chunk = plugin.getServer().getScheduler().callSyncMethod(plugin, new Callable<ChunkSnapshot>() {
                    @Override
                    public ChunkSnapshot call() throws Exception {
                        return world.getChunkAt(x, z).getChunkSnapshot(false, true, false);
                    }
                }).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        if (chunk == null) {
            ByteBuf data = getChunkData(x, z);
            if (data == null) {
                return false;
            }
            out.writeBytes(data);
            data.release();
            return true;
        }
        gzipChunk(chunk, out);
        return true;
    }

    private void gzipChunk(ChunkSnapshot chunk, ByteBuf out) {
        int mask = 0;
        int count = 0;
        for (int i = 0; i < 16; i++) {
            if (!chunk.isSectionEmpty(i)) {
                mask |= 1 << i;
                count++;
            }
        }
        ByteBuf data = Unpooled.buffer(16 * 16 * 16 * 5 * count);
        data.writeInt(chunk.getX());
        data.writeInt(chunk.getZ());
        data.writeShort(mask);
        for (int i = 0; i < 16; i++) {
            if (!chunk.isSectionEmpty(i)) {
                for (int oy = 0; oy < 16; oy++) {
                    for (int oz = 0; oz < 16; oz++) {
                        for (int ox = 0; ox < 16; ox++) {
                            data.writeShort(chunk.getBlockTypeId(ox, oy + i * 16, oz));
                            data.writeByte(chunk.getBlockData(ox, oy + i * 16, oz));
                            data.writeByte(chunk.getBlockEmittedLight(ox, oy + i * 16, oz));
                            data.writeByte(chunk.getBlockSkyLight(ox, oy + i * 16, oz));
                        }
                    }
                }
            }
        }
        try {
            GZIPOutputStream gzip = new GZIPOutputStream(new ByteBufOutputStream(out));
            gzip.write(data.array());
            gzip.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        data.release();
    }
}
