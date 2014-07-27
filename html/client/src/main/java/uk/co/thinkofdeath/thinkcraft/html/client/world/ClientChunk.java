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

package uk.co.thinkofdeath.thinkcraft.html.client.world;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayInteger;
import uk.co.thinkofdeath.thinkcraft.html.client.render.ChunkRenderObject;
import uk.co.thinkofdeath.thinkcraft.html.client.render.SortableRenderObject;
import uk.co.thinkofdeath.thinkcraft.shared.block.Block;
import uk.co.thinkofdeath.thinkcraft.shared.model.PositionedModel;
import uk.co.thinkofdeath.thinkcraft.shared.support.TUint8Array;
import uk.co.thinkofdeath.thinkcraft.shared.worker.ChunkLoadedMessage;
import uk.co.thinkofdeath.thinkcraft.shared.worker.FreeMessage;
import uk.co.thinkofdeath.thinkcraft.shared.world.Chunk;
import uk.co.thinkofdeath.thinkcraft.shared.world.ChunkSection;

import java.util.ArrayList;

public class ClientChunk extends Chunk {

    private final ClientWorld world;
    // Sections of the chunk that need (re)building
    private final boolean[] outdatedSections = new boolean[16];
    private final int[] buildNumbers = new int[16];
    private final ChunkRenderObject[] renderObjects = new ChunkRenderObject[16];
    private int lastBuildNumber = 0;
    private SortableRenderObject[] sortableRenderObjects = new SortableRenderObject[16];

    /**
     * Creates a client-side chunk
     *
     * @param world
     *         The world which owns this chunk
     * @param chunkLoadedMessage
     *         The message containing the data required to load the chunk
     */
    public ClientChunk(ClientWorld world, ChunkLoadedMessage chunkLoadedMessage) {
        super(world, chunkLoadedMessage.getX(), chunkLoadedMessage.getZ());
        this.world = world;

        for (int i = 0; i < 16; i++) {
            sections[i] = extractSection(chunkLoadedMessage, i);
            outdatedSections[i] = sections[i] != null;
        }

        extractChunk(chunkLoadedMessage);
        nextId = chunkLoadedMessage.getNextId();

        biomes = chunkLoadedMessage.getBiomes();
    }

    /**
     * Updates the chunk's state
     */
    public void update() {
        // Check for sections that need rebuilding
        for (int i = 0; i < 16; i++) {
            if (sections[i] != null && outdatedSections[i]) {
                outdatedSections[i] = false;
                world.requestBuild(this, i, ++lastBuildNumber);
            }
        }
    }

    public boolean checkAndSetBuildNumber(int buildNumber, int sectionNumber) {
        if (buildNumber < buildNumbers[sectionNumber] || isUnloaded()) {
            return false;
        }
        buildNumbers[sectionNumber] = buildNumber;
        return true;
    }

    public void setTransparentModels(int i, JsArray<PositionedModel> trans, TUint8Array transData, int sender) {
        if (sortableRenderObjects[i] != null) {
            TUint8Array data = sortableRenderObjects[i].getData();
            world.mapViewer.getWorkerPool().sendMessage(sender, new FreeMessage(data), false, data.getBuffer());
        }
        if (sortableRenderObjects[i] != null && trans.length() == 0) {
            world.mapViewer.getRenderer().removeSortable(sortableRenderObjects[i]);
            return;
        }
        if (trans.length() > 0) {
            if (sortableRenderObjects[i] == null) {
                sortableRenderObjects[i] = new SortableRenderObject(getX(), i, getZ());
                world.mapViewer.getRenderer().postSortable(sortableRenderObjects[i]);
            }
            ArrayList<PositionedModel> models = sortableRenderObjects[i].getModels();
            models.clear();
            for (int j = 0; j < trans.length(); j++) {
                models.add(trans.get(j));
            }
            sortableRenderObjects[i].setData(transData);
        }
    }

    public void updateAccess(int sectionNumber, JsArrayInteger accessData) {
        ChunkSection section = sections[sectionNumber];
        for (int j = 0; j < accessData.length(); j++) {
            section.getSideAccess()[j] = accessData.get(j);
        }
    }

    /**
     * Creates and fills this chunk's WebGL buffer
     *
     * @param sectionNumber
     *         The section number
     * @param data
     *         The data
     */
    public void fillBuffer(int sectionNumber, TUint8Array data, int sender) {
        if (data.length() == 0) {
            if (renderObjects[sectionNumber] != null) {
                world.mapViewer.getRenderer().removeChunkObject(renderObjects[sectionNumber]);
            }
        } else {
            if (renderObjects[sectionNumber] == null) {
                renderObjects[sectionNumber] = new ChunkRenderObject(this, getX(), sectionNumber, getZ());
            }
            world.mapViewer.getRenderer().updateChunkObject(renderObjects[sectionNumber], data, sender);
        }
    }

    /**
     * Flags the chunk for rebuilding
     */
    public void rebuild() {
        for (int i = 0; i < 16; i++) {
            outdatedSections[i] = true;
        }
    }

    @Override
    public void unload() {
        super.unload();
        for (ChunkRenderObject renderObject : renderObjects) {
            if (renderObject != null) {
                world.mapViewer.getRenderer().removeChunkObject(renderObject);
            }
        }
        for (SortableRenderObject sortableRenderObject : sortableRenderObjects) {
            if (sortableRenderObject != null) {
                world.mapViewer.getRenderer().removeSortable(sortableRenderObject);
            }
        }
    }

    public ChunkRenderObject[] getRenderObjects() {
        return renderObjects;
    }

    private native void extractChunk(ChunkLoadedMessage chunkLoadedMessage)/*-{
        var that = chunkLoadedMessage.@uk.co.thinkofdeath.thinkcraft.shared.worker.ChunkLoadedMessage::nativeVoodoo;
        var idMap = this.@uk.co.thinkofdeath.thinkcraft.shared.world.Chunk::idBlockMap;
        var blockMap = this.@uk.co.thinkofdeath.thinkcraft.shared.world.Chunk::blockIdMap;
        for (var key in that.idmap) {
            if (that.idmap.hasOwnProperty(key)) {
                var k = parseInt(key);
                var val = that.idmap[k];
                var name = val[0];
                var raw = val[1]
                var block = this.@uk.co.thinkofdeath.thinkcraft.html.client.world.ClientChunk::_js_toBlock(Ljava/lang/String;I)(name, raw);
                idMap.@uk.co.thinkofdeath.thinkcraft.shared.util.IntMap::put(ILjava/lang/Object;)(k, block);
                blockMap.@java.util.Map::put(Ljava/lang/Object;Ljava/lang/Object;)(block, @java.lang.Integer::valueOf(I)(k));
            }
        }
    }-*/;

    // Short-cut method for JSNI code
    private Block _js_toBlock(String name, int rawState) {
        return world.getMapViewer().getBlockRegistry().get(name, rawState);
    }

    private native ChunkSection extractSection(ChunkLoadedMessage message, int i)/*-{
        var that = message.@uk.co.thinkofdeath.thinkcraft.shared.worker.ChunkLoadedMessage::nativeVoodoo;
        var jssection = that.sections[i];
        if (jssection == null) {
            return null;
        }
        var section = @uk.co.thinkofdeath.thinkcraft.shared.world.ChunkSection::new(Luk/co/thinkofdeath/thinkcraft/shared/support/TUint8Array;)(jssection.buffer);
        section.@uk.co.thinkofdeath.thinkcraft.shared.world.ChunkSection::count = jssection.count;
        return section;
    }-*/;
}
