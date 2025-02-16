package jet.opengl.particles;

import org.lwjgl.util.vector.ReadableVector3f;
import org.lwjgl.util.vector.Vector3f;

import jet.opengl.postprocessing.util.Numeric;

public class RangeVector3f {
    private final Vector3f start = new Vector3f();
    private final Vector3f end = new Vector3f();

    public RangeVector3f(){}

    public RangeVector3f(ReadableVector3f start, ReadableVector3f end){
        this.start.set(start);
        this.end.set(end);
    }

    public void eval(float time, Vector3f result){
        time = Numeric.clamp(time, 0, 1);
        Vector3f.mix(start,end, time, result);
    }

    public void setStart(ReadableVector3f start){
        this.start.set(start);
    }

    public void setEnd(ReadableVector3f end){
        this.end.set(end);
    }

    public void set(RangeVector3f ohs){
        this.start.set(ohs.start);
        this.end.set(ohs.end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RangeVector3f that = (RangeVector3f) o;

        if (!start.equals(that.start)) return false;
        return end.equals(that.end);
    }

    @Override
    public int hashCode() {
        int result = start != null ? start.hashCode() : 0;
        result = 31 * result + (end != null ? end.hashCode() : 0);
        return result;
    }
}
