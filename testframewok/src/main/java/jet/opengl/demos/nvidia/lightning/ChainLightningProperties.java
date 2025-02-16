package jet.opengl.demos.nvidia.lightning;

import org.lwjgl.util.vector.Readable;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.ByteBuffer;

import jet.opengl.postprocessing.util.CacheBuffer;

/**
 * Created by mazhen'gui on 2017/8/28.
 */

final class ChainLightningProperties implements Readable{
    static final int MaxTargets = 8;
    static final int SIZE = Vector4f.SIZE + Vector4f.SIZE * MaxTargets + Vector4f.SIZE;

    final Vector3f ChainSource = new Vector3f();

    final Vector4f[] ChainTargetPositions = new Vector4f[MaxTargets];
    int			NumTargets;

    ChainLightningProperties(){
        for(int i=0;i < ChainTargetPositions.length; i++)
            ChainTargetPositions[i] = new Vector4f();
    }

    @Override
    public ByteBuffer store(ByteBuffer buf) {
        ChainSource.store(buf);
        buf.putFloat(0);
        CacheBuffer.put(buf, ChainTargetPositions);
        buf.putInt(NumTargets);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);

        return buf;
    }
}
