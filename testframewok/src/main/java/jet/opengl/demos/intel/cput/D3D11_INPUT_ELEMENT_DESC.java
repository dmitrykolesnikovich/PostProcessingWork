package jet.opengl.demos.intel.cput;

/**
 * Created by mazhen'gui on 2017/11/15.
 */

public final class D3D11_INPUT_ELEMENT_DESC {
    public String SemanticName;
    public int SemanticIndex;
    public int Format;
    public int InputSlot;
    public int AlignedByteOffset;
    public int InputSlotClass;
    public int InstanceDataStepRate;

    public D3D11_INPUT_ELEMENT_DESC(){}

    public D3D11_INPUT_ELEMENT_DESC(String semanticName, int semanticIndex, int format, int inputSlot, int alignedByteOffset, int inputSlotClass, int instanceDataStepRate) {
        SemanticName = semanticName;
        SemanticIndex = semanticIndex;
        Format = format;
        InputSlot = inputSlot;
        AlignedByteOffset = alignedByteOffset;
        InputSlotClass = inputSlotClass;
        InstanceDataStepRate = instanceDataStepRate;
    }

    public void zeros(){
        SemanticName = null;
        SemanticIndex = 0;
        Format = 0;
        InputSlot = 0;
        AlignedByteOffset = 0;
        InputSlotClass = 0;
        InstanceDataStepRate = 0;
    }
}
