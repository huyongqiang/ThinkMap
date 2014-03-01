part of map_viewer;

abstract class World {

  static final Logger logger = new Logger("World");

  Map<String, Chunk> chunks = new Map();
  Map<String, bool> chunksLoading = new Map();

  int currentTime = 6000;
  IsolateWorldProxy proxy;
  bool needSort = false;

  World() {
    if (!(this is IsolateWorld)) proxy = new IsolateWorldProxy(this);
    new Timer.periodic(new Duration(milliseconds: 1000 ~/ 20), tick);
  }

  void tick(Timer timer) {
    currentTime += 1;
    currentTime %= 24000;
  }

  /**
   * Request a chunk from the server
   */
  void writeRequestChunk(int x, int z) {
    proxy.requestChunk(x, z);
  }

  void addChunk(Chunk chunk) {
    String key = _chunkKey(chunk.x, chunk.z);
    if (chunks[key] != null) {
      // Chunk is already loaded ignore it
      return;
    }
    chunks[key] = chunk;
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        Chunk c = getChunk(chunk.x + x, chunk.z + z);
        if (c != null) c.rebuild();
      }
    }
    chunk.rebuild();
  }

  void removeChunk(int x, int z) {
    Chunk chunk = getChunk(x, z);
    chunks.remove(_chunkKey(x, z));
    chunk.unload(renderer);
    for (int i = 0; i < 16; i++) {
      _waitingForBuild.remove(_buildKey(x, z, i));
    }
  }

  // Build related methods

  Map<String, bool> _waitingForBuild = new Map();
  List<_BuildJob> _buildQueue = new List();
  _BuildJob currentBuild;
  Object currentSnapshot;

  void requestBuild(Chunk chunk, int i) {
    String key = _buildKey(chunk.x, chunk.z, i);
    if (_waitingForBuild.containsKey(key)) {
      // Already queued
      return;
    }
    _waitingForBuild[key] = true;
    _buildQueue.add(new _BuildJob(chunk, i));
    needSort = true;
  }

  static int BUILD_LIMIT_MS = 8000;

  void tickBuildQueue(Stopwatch stopwatch) {
    if (currentBuild != null) {
      var job = currentBuild;
      if (world.getChunk(job.chunk.x, job.chunk.z) == null) {
        job.drop(currentSnapshot);
        currentBuild = null;
        currentSnapshot = null;
      } else {
        Object snapshot = job.exec(currentSnapshot, stopwatch);
        currentBuild = null;
        currentSnapshot = null;
        if (snapshot != null) {
          currentBuild = job;
          currentSnapshot = snapshot;
          return;
        }
      }
    }

    if (stopwatch.elapsedMicroseconds > World.BUILD_LIMIT_MS) {
      return;
    }

    if (_buildQueue.isNotEmpty && needSort) {
      needSort = false;
      _buildQueue.sort(_queueCompare);
    }
    while (stopwatch.elapsedMicroseconds < BUILD_LIMIT_MS && _buildQueue.isNotEmpty) {
      var job = _buildQueue.removeLast();
      String key = _buildKey(job.chunk.x, job.chunk.z, job.i);
      if (!_waitingForBuild.containsKey(key)) {
        continue;
      }
      _waitingForBuild.remove(key);
      if (world.getChunk(job.chunk.x, job.chunk.z) == null) {
        continue;
      }
      Object snapshot = job.exec(null, stopwatch);
      if (snapshot != null) {
        currentBuild = job;
        currentSnapshot = snapshot;
        return;
      }
    }
  }

  Chunk newChunk();

  int _queueCompare(_BuildJob a, _BuildJob b);

  // General methods

  int cacheX;
  int cacheZ;
  Chunk cacheChunk;

  Chunk getChunk(int x, int z) {
    x = x.toSigned(32);
    z = z.toSigned(32);
    if (cacheChunk != null && cacheX == x && cacheZ == z) {
      return cacheChunk;
    }
    cacheX = x;
    cacheZ = z;
    cacheChunk = chunks[_chunkKey(x, z)];
    return cacheChunk;
  }

  Block getBlock(int x, int y, int z) {
    if (y < 0 || y > 255) return Blocks.AIR;
    int cx = x >> 4;
    int cz = z >> 4;
    var chunk = getChunk(cx, cz);
    if (chunk == null) {
      return Blocks.NULL_BLOCK;
    }
    return chunk.getBlock(x & 0xF, y, z & 0xF);
  }

  int getLight(int x, int y, int z) {
    if (y < 0 || y > 255) return 0;
    int cx = x >> 4;
    int cz = z >> 4;
    var chunk = getChunk(cx, cz);
    if (chunk == null) {
      return 0;
    }
    return chunk.getLight(x & 0xF, y, z & 0xF);
  }

  int getSky(int x, int y, int z) {
    if (y < 0 || y > 255) return 15;
    int cx = x >> 4;
    int cz = z >> 4;
    var chunk = getChunk(cx, cz);
    if (chunk == null) {
      return 15;
    }
    return chunk.getSky(x & 0xF, y, z & 0xF);
  }

  bool isLoaded(int x, int y, int z) {
    if (y < 0 || y > 255) return false;
    int cx = x >> 4;
    int cz = z >> 4;
    return getChunk(cx, cz) != null;
  }

  static String _chunkKey(int x, int z) => "${x}:${z}";

  static String _buildKey(int x, int z, int i) => "${x.toSigned(32)}:${z.toSigned(32)}@$i";
}
