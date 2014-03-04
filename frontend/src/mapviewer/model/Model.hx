package mapviewer.model;
import mapviewer.block.Block.Face;
import mapviewer.model.Model.ModelFace;
import mapviewer.model.Model.ModelVertex;
import mapviewer.renderer.LightInfo;
import mapviewer.renderer.webgl.BlockBuilder;
import mapviewer.renderer.webgl.glmatrix.Quat;
import mapviewer.utils.Chainable;
import mapviewer.world.Chunk;

class Model {
	
	public static var models : Map<String, Model> = new Map();
	
	inline public static function get(name : String) { return models[name];  }

	public var faces : Array<ModelFace>;
	
	public function new() {
		faces = new Array();
	}
	
	public function fromMap(input : Dynamic) {
		var fs : Array<Dynamic> = input.faces;
		for (iFace in fs) {
			var face = new ModelFace(Face.fromName(iFace.face));
			faces.push(face);
			face.texture = iFace.texture;
			face.r = iFace.colour.r;
			face.g = iFace.colour.g;
			face.b = iFace.colour.b;
			var vs : Array<Dynamic> = iFace.vertices;
			for (iVert in vs) {
				var vert = new ModelVertex(
					iVert.x / 16,
					iVert.y / 16,
					iVert.z / 16,
					iVert.textureX / 16,
					iVert.textureY / 16
				);
				face.vertices.push(vert);
			}
		}
	}
	
	public function render(builder : BlockBuilder, x : Int, y : Int, z : Int, chunk : Chunk) {
		var light = new LightInfo(chunk.getLight(x, y, z), chunk.getSky(x, y, z));
		for (face in faces) {
			var texture = Main.blockTextureInfo[face.texture];
			for (i in 0 ... 3) {
				var vert = face.vertices[i];
				builder
					.position(x + vert.x, y + vert.y, z + vert.z)
					.colour(face.r, face.g, face.b)
					.tex(vert.textureX, vert.textureY)
					.texId(texture.start, texture.end)
					.lighting(light.light, light.sky);
			}
			for (i in 0 ... 3) {				
				var vert = face.vertices[3 - i];
				builder
					.position(x + vert.x, y + vert.y, z + vert.z)
					.colour(face.r, face.g, face.b)
					.tex(vert.textureX, vert.textureY)
					.texId(texture.start, texture.end)
					.lighting(light.light, light.sky);
			}
		}
	}
	
	public function rotateY(deg : Float) : Model {
		rotate(deg, [0.0, 1.0, 0.0]);		
		//TODO: Rotate faces
		return this;
	}
	
	public function rotateX(deg : Float) : Model {
		rotate(deg, [1.0, 0.0, 0.0]);
		return this;
	}
	
	public function rotateZ(deg : Float) : Model {
		rotate(deg, [0.0, 0.0, 1.0]);	
		return this;	
	}
	
	private function rotate(deg : Float, axis : Array<Float>) {
		var q = Quat.create();
		var t1 = Quat.create();
		var t2 = Quat.create();
		q.setAxisAngle(axis, Math.PI / 180 * deg);
		untyped quat.conjugate(t1, q);
		for (face in faces) {
			for (vert in face.vertices) {
				var vec = [vert.x - 0.5, vert.y - 0.5, vert.z - 0.5, 0];
				untyped quat.multiply(t2, quat.multiply(t2, t1, vec), q);
				vert.x = t2[0] + 0.5;
				vert.y = t2[1] + 0.5;
				vert.z = t2[2] + 0.5;
			}
		}
	}
	
	private static function noopTextureGetter(texture : String) : String {
		return texture;
	}
	
	public function clone(?getTexture : String -> String) : Model {
		if (getTexture == null) { getTexture = noopTextureGetter; }
		var out = new Model();
		for (face in faces) {
			var newFace = new ModelFace(face.face);
			newFace.texture = getTexture(face.texture);
			newFace.r = face.r;
			newFace.g = face.g;
			newFace.b = face.b;
			out.faces.push(newFace);
			for (vert in face.vertices) {
				newFace.vertices.push(vert.clone());
			}
		}
		return out;
	}
	
}

class ModelFace implements Chainable {
	
	private static var defaultFaces: Map <String, Array<ModelVertex>> = [
		"top" => [
		  new ModelVertex(0, 0, 0, 0, 0),
		  new ModelVertex(1, 0, 0, 1, 0),
		  new ModelVertex(0, 0, 1, 0, 1),
		  new ModelVertex(1, 0, 1, 1, 1)
		],
		"bottom" => [
		  new ModelVertex(0, 0, 0, 0, 0),
		  new ModelVertex(0, 0, 1, 0, 1),
		  new ModelVertex(1, 0, 0, 1, 0),
		  new ModelVertex(1, 0, 1, 1, 1)
		],
		"left" => [
		  new ModelVertex(0, 0, 0, 1, 1),
		  new ModelVertex(0, 0, 1, 0, 1),
		  new ModelVertex(0, 1, 0, 1, 0),
		  new ModelVertex(0, 1, 1, 0, 0)
		],
		"right" => [
		  new ModelVertex(0, 0, 0, 0, 1),
		  new ModelVertex(0, 1, 0, 0, 0),
		  new ModelVertex(0, 0, 1, 1, 1),
		  new ModelVertex(0, 1, 1, 1, 0)	
		],
		"front" => [
		  new ModelVertex(0, 0, 0, 0, 1),
		  new ModelVertex(0, 1, 0, 0, 0),
		  new ModelVertex(1, 0, 0, 1, 1),
		  new ModelVertex(1, 1, 0, 1, 0)
		],
		"back" => [
		  new ModelVertex(0, 0, 0, 1, 1),
		  new ModelVertex(1, 0, 0, 0, 1),
		  new ModelVertex(0, 1, 0, 1, 0),
		  new ModelVertex(1, 1, 0, 0, 0)
		]
	];
	
	@:chain public var texture : String;
	public var vertices : Array<ModelVertex>;
	public var face : Face;
	@:chain public var r : Int = 255;
	@:chain public var g : Int = 255;
	@:chain public var b : Int = 255;
	
	public function new(face : Face) {
		vertices = new Array();
	}
	
	public static function fromFace(face : Face) : ModelFace {
		var f = new ModelFace(face);
		for (vert in defaultFaces[face.name]) {
			f.vertices.push(vert.clone());
		}
		return f;
	}

	public function moveY(a : Float, ?tex : Bool = false) : ModelFace {
		for (vert in vertices) {
			if (!tex)
				vert.y += a / 16;
			else
				vert.textureY += a / 16;
		}
		return this;
	}

	public function moveX(a : Float, ?tex : Bool = false) : ModelFace {
		for (vert in vertices) {
			if (!tex)
				vert.x += a / 16;
			else
				vert.textureX += a / 16;
		}
		return this;
	}

	public function moveZ(a : Float, ?tex : Bool = false) : ModelFace {
		for (vert in vertices) {
			vert.z += a / 16;
		}
		return this;
	}

	public function sizeY(a : Float, ?tex : Bool = false) : ModelFace {
		var largest : Float = 0;
		if (!tex) {
			for (vert in vertices) {
				if (vert.y > largest) largest = vert.y;
			}
			for (vert in vertices) {
				if (vert.y == largest) {
					vert.y += a / 16;
				}
			}
		} else {
			for (vert in vertices) {
				if (vert.textureY > largest) largest = vert.textureY;
			}
			for (vert in vertices) {
				if (vert.textureY == largest) {
					vert.textureY += a / 16;
				}
			}
		}
		return this;
	}

	public function sizeX(a : Float, ?tex : Bool = false) : ModelFace {
		var largest : Float = 0;
		if (!tex) {
			for (vert in vertices) {
				if (vert.x > largest) largest = vert.x;
			}
			for (vert in vertices) {
				if (vert.x == largest) {
					vert.x += a / 16;
				}
			}
		} else {
			for (vert in vertices) {
				if (vert.textureX > largest) largest = vert.textureX;
			}
			for (vert in vertices) {
				if (vert.textureX == largest) {
					vert.textureX += a / 16;
				}
			}
		}
		return this;
	}

	public function sizeZ(a : Float, ?tex : Bool = false) : ModelFace {
		var largest : Float = 0;
		for (vert in vertices) {
			if (vert.z > largest) largest = vert.z;
		}
		for (vert in vertices) {
			if (vert.z == largest) {
				vert.z += a / 16;
			}
		}
		return this;
	}
}

class ModelVertex {
	
	public var x : Float;
	public var y : Float;
	public var z : Float;
	public var textureX : Float;
	public var textureY : Float;
	
	public function new(x : Float, y : Float, z : Float, textureX : Float, textureY : Float) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.textureX = textureX;
		this.textureY = textureY;
	}
	
	public function clone() : ModelVertex {
		return new ModelVertex(x, y, z, textureX, textureY);
	}
}