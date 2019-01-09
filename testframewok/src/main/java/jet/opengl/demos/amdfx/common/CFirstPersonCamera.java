package jet.opengl.demos.amdfx.common;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.ReadableVector3f;
import org.lwjgl.util.vector.Vector3f;

public class CFirstPersonCamera {

	private final Matrix4f m_View = new Matrix4f();
	private final Matrix4f m_Proj = new Matrix4f();
	
	private final Vector3f m_Eye = new Vector3f();
	private final Vector3f m_LookAt = new Vector3f();
	private final Vector3f m_Up  = new Vector3f();
	
	private float m_Fov;
	private float m_Aspect;
	private float m_Near;
	private float m_Far;
	
	public void SetProjParams(float fFOV, float fAspect, float fNearPlane, float fFarPlane){
		m_Fov = fFOV;
		m_Aspect = fAspect;
		m_Near = fNearPlane;
		m_Far = fFarPlane;
		
		Matrix4f.perspective(fFOV, fAspect, fNearPlane, fFarPlane, m_Proj);
	}

	public void SetViewParams( ReadableVector3f vEyePt, ReadableVector3f vLookatPt, ReadableVector3f vUp ){
		Matrix4f.lookAt(vEyePt, vLookatPt, vUp, m_View);
		Matrix4f.decompseRigidMatrix(m_View, m_Eye, null, m_Up);
		m_LookAt.set(vLookatPt);
	}

	public void SetViewParams(Matrix4f view){
		m_View.load(view);
		Matrix4f.decompseRigidMatrix(m_View, m_Eye, m_LookAt, m_Up);
	}
	
	public Matrix4f GetViewMatrix() { return m_View;}
	public Matrix4f GetProjMatrix() { return m_Proj;}
	public Vector3f GetEyePt()      { return m_Eye;}
	public Vector3f GetWorldUp()    { return m_Up;}
	public Vector3f GetLookAtPt()   { return m_LookAt;}
	public float    GetFOV()        { return m_Fov;}
	public float    GetAspect()     { return m_Aspect;}
	public float    GetNearClip()   { return m_Near;}
	public float    GetFarClip()    { return m_Far;}
}
