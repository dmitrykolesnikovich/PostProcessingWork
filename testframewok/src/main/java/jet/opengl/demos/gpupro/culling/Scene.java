package jet.opengl.demos.gpupro.culling;

import com.nvidia.developer.opengl.app.NvInputTransformer;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jet.opengl.postprocessing.util.Pair;
import jet.opengl.postprocessing.util.Pool;
import jet.opengl.postprocessing.util.StackBool;
import jet.opengl.postprocessing.util.StackInt;

final class Scene {
    // The model sources.
    final List<Model> mModels = new ArrayList<>();

    // processed attributes for fast rendering.
    final List<Mesh> mExpandMeshes = new ArrayList<>();
    final StackBool mExpandMeshVisible = new StackBool();
    final List<Material> mMaterials = new ArrayList<>();
    final StackInt mMeshMaterials = new StackInt();  // The material id for the expand meshes.
    final StackInt mMeshModels = new StackInt();
    final HashMap<MeshType, List<Pair<Mesh, Integer>>> instanceMeshes = new HashMap<>();

    final StackInt mSolidMeshes = new StackInt();
    final StackInt mTransparencyMeshes = new StackInt();

    final List<InstanceMesh> mInstanceSolidMeshes = new ArrayList<>();
    final List<InstanceMesh> mInstanceTransparancyMeshes = new ArrayList<>();
    static final Pool<InstanceMesh> gInstanceMeshCache = new Pool<>(()->new InstanceMesh());
    static final Pool<InstanceData> gTransfomCache = new Pool<>(()->new InstanceData());

    // Camera attributes
    final Matrix4f mView = new Matrix4f();
    final Matrix4f mProj = new Matrix4f();
    final Vector3f mEye = new Vector3f();
    final Vector3f mCameraForward = new Vector3f();

    final Matrix4f mPrevView = new Matrix4f();
//    final Matrix4f mPrevProj = new Matrix4f();
//    final Recti mViewport = new Recti();

    int mViewWidth, mViewHeight;

    void buildMeshInformations(){
        mExpandMeshes.clear();
        mExpandMeshVisible.clear();
        mMaterials.clear();
        mMeshMaterials.clear();
        mMeshModels.clear();
        instanceMeshes.clear();

        int modelIndex = 0;
        for(Model model : mModels){
            int index = addMaterial(model.mMaterial);
            for(Mesh mesh : model.mMeshes){
                mExpandMeshes.add(mesh);
                mMeshMaterials.push(index);
                mMeshModels.push(modelIndex);

                if(mesh.mType == null)
                    throw new NullPointerException();

                List<Pair<Mesh, Integer>> instances = instanceMeshes.get(mesh.mType);
                if(instances == null){
                    instances = new ArrayList<>();
                    instanceMeshes.put(mesh.mType, instances);
                }

                instances.add(new Pair<>(mesh, mExpandMeshes.size()));

                final int meshIdx = mExpandMeshes.size();
                if(model.mMaterial.mTransparency){
                    mTransparencyMeshes.push(meshIdx);
                }else{
                    mSolidMeshes.push(meshIdx);
                }
            }

            modelIndex++;
        }

        mExpandMeshVisible.resize(mExpandMeshes.size());
        mExpandMeshVisible.fill(0, mExpandMeshVisible.size(), true);
    }

    private final int addMaterial(Material material){
        int idx = mMaterials.indexOf(material);
        if(idx < 0){
            mMaterials.add(material);
            idx = mMaterials.size() - 1;
        }

        return idx;
    }

    void updateCamera(NvInputTransformer camera){
        mPrevView.load(mView);

        camera.getModelViewMat(mView);
        Matrix4f.decompseRigidMatrix(mView, mEye, null, null, mCameraForward);
        mCameraForward.scale(-1);

        mExpandMeshVisible.fill(0, mExpandMeshVisible.size(), true);


        // camera culling
    }

    private void freeAllInstance(List<InstanceMesh> instanceMeshes){
        if(!instanceMeshes.isEmpty()){
            gInstanceMeshCache.freeAll(instanceMeshes);
            for(InstanceMesh mesh : instanceMeshes){
                mesh.reset();
            }

            instanceMeshes.clear();
        }
    }

    void prepareInstanceRender(){
        freeAllInstance(mInstanceSolidMeshes);
        freeAllInstance(mInstanceTransparancyMeshes);

        for(Map.Entry<MeshType, List<Pair<Mesh, Integer>>> entry : instanceMeshes.entrySet()){
            List<Pair<Mesh, Integer>> meshes = entry.getValue();
            if(meshes == null || meshes.isEmpty())
                continue;

            InstanceMesh instanceSolidMesh = gInstanceMeshCache.obtain();
            instanceSolidMesh.mType = entry.getKey();
            instanceSolidMesh.mMesh = meshes.get(0).first.mVao;

            InstanceMesh instanceTransparencyMesh = gInstanceMeshCache.obtain();
            instanceTransparencyMesh.mType = entry.getKey();
            instanceTransparencyMesh.mMesh = meshes.get(0).first.mVao;

            int instanceCount = meshes.size();
            for(int i = 0; i < instanceCount; i++){
                Pair<Mesh, Integer> instance = meshes.get(i);

                if(mExpandMeshVisible.get(instance.second)){
                    final int materialID = mMeshMaterials.get(instance.second);
                    InstanceData data = gTransfomCache.obtain();
                    data.mMeshIndex = instance.second;

                    data.mMaterialID =materialID;
                    data.mWorld = instance.first.mWorld;

                    if(!mMaterials.get(materialID).mTransparency) {
                        instanceSolidMesh.mData.add(data);
                    }else{
                        instanceTransparencyMesh.mData.add(data);
                    }
                }
            }

            if(instanceSolidMesh.mData.size() > 0) {
                mInstanceSolidMeshes.add(instanceSolidMesh);
            }else{
                gInstanceMeshCache.free(instanceSolidMesh);
            }

            if(instanceTransparencyMesh.mData.size() > 0) {
                mInstanceTransparancyMeshes.add(instanceTransparencyMesh);
            }else{
                gInstanceMeshCache.free(instanceTransparencyMesh);
            }
        }
    }

    void onResize(int width, int height){
        Matrix4f.perspective(60, (float)width/height, 0.1f, 1000, mProj);

        mViewWidth = width;
        mViewHeight = height;
    }
}
