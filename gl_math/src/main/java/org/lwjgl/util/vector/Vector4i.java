package org.lwjgl.util.vector;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Vector4i implements Readable, Writable {
	public static final int SIZE = 16;

	public int x,y,z,w;
	
	public Vector4i() {
	}

	public Vector4i(int x, int y,int z, int w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}
	
	public Vector4i(Vector4i v) {
		set(v);
	}
	
	public void set(int x, int y,int z, int w){
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}

	public void set(Vector3i v){
		x = v.x;
		y = v.y;
		z = v.z;
	}
	
	public void set(Vector4i v){
		x = v.x;
		y = v.y;
		z = v.z;
		w = v.w;
	}
	
	public static String toString(Vector4i v){
		StringBuilder sb = new StringBuilder(16);

		sb.append("[");
		sb.append(v.x);
		sb.append(", ");
		sb.append(v.y);
		sb.append(", ");
		sb.append(v.z);
		sb.append(", ");
		sb.append(v.w);
		sb.append(']');
		return sb.toString();
	}

	public String toString() {
		return toString(this);
	}

	public void load(IntBuffer buf) {
		x = buf.get();
		y = buf.get();
		z = buf.get();
		w = buf.get();
	}

	@Override
	public ByteBuffer store(ByteBuffer buf){
		buf.putInt(x);
		buf.putInt(y);
		buf.putInt(z);
		buf.putInt(w);
		return buf;
	}

	@Override
	public Vector4i load(ByteBuffer buf) {
		x = buf.getInt();
		y = buf.getInt();
		z = buf.getInt();
		w = buf.getInt();
		return this;
	}
}
