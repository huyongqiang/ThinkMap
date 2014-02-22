part of map_viewer;

class BlockBuilder {

  DynamicUint8List _buffer;

  BlockBuilder() {
    _buffer = new DynamicUint8List(80000);
  }

  void position(num x, num y, num z) {
    _buffer.addUnsignedShort((x * 16).toInt());
    _buffer.addUnsignedShort((y * 16).toInt());
    _buffer.addUnsignedShort((z * 16).toInt());
  }

  void colour(int r, int g, int b) {
    _buffer.add(r);
    _buffer.add(g);
    _buffer.add(b);
    _buffer.add(0); // Padding
  }

  void texId(int start, int end) {
    _buffer.addUnsignedShort(start);
    _buffer.addUnsignedShort(end);
  }

  void tex(num x, num y) {
    _buffer.addUnsignedShort((x * 16).toInt());
    _buffer.addUnsignedShort((y * 16).toInt());
  }

  void lighting(int light, int sky) {
    _buffer.add(light & 0xFF);
    _buffer.add(sky & 0xFF);
  }

  Uint8List toTypedList() {
    var ret = _buffer.getList();
    _buffer.free();
    return ret;
  }
}