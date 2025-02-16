package assimp.common;

/**
 * Defines the purpose of a texture<p>
*
*  This is a very difficult topic. Different 3D packages support different
*  kinds of textures. For very common texture types, such as bumpmaps, the
*  rendering results depend on implementation details in the rendering 
*  pipelines of these applications. Assimp loads all texture references from
*  the model file and tries to determine which of the predefined texture
*  types below is the best choice to match the original use of the texture
*  as closely as possible.<br>
*  
*  In content pipelines you'll usually define how textures have to be handled,
*  and the artists working on models have to conform to this specification,
*  regardless which 3D tool they're using.
*/
public enum TextureType {

	/** Dummy value.<p>
	 *
	 *  No texture, but the value to be used as 'texture semantic' 
	 *  (#aiMaterialProperty::mSemantic) for all material properties 
	 *  *not* related to textures.
	 */
	aiTextureType_NONE /*= 0x0*/,

   /** The texture is combined with the result of the diffuse
	 *  lighting equation.
    */
   aiTextureType_DIFFUSE /*= 0x1*/,

	/** The texture is combined with the result of the specular
	 *  lighting equation.
    */
   aiTextureType_SPECULAR /*= 0x2*/,

	/** The texture is combined with the result of the ambient
	 *  lighting equation.
    */
   aiTextureType_AMBIENT /*= 0x3*/,

	/** The texture is added to the result of the lighting
	 *  calculation. It isn't influenced by incoming light.
    */
   aiTextureType_EMISSIVE /*= 0x4*/,

	/** The texture is a height map.<p>
	 *
	 *  By convention, higher gray-scale values stand for
	 *  higher elevations from the base height.
    */
   aiTextureType_HEIGHT /*= 0x5*/,

	/** The texture is a (tangent space) normal-map.<p>
	 *
	 *  Again, there are several conventions for tangent-space
	 *  normal maps. Assimp does (intentionally) not 
	 *  distinguish here.
    */
   aiTextureType_NORMALS /*= 0x6*/,

	/** The texture defines the glossiness of the material.<p>
	 *
	 *  The glossiness is in fact the exponent of the specular
	 *  (phong) lighting equation. Usually there is a conversion
	 *  function defined to map the linear color values in the
	 *  texture to a suitable exponent. Have fun.
   */
   aiTextureType_SHININESS /*= 0x7*/,

	/** The texture defines per-pixel opacity.<p>
	 *
	 *  Usually 'white' means opaque and 'black' means 
	 *  'transparency'. Or quite the opposite. Have fun.
   */
   aiTextureType_OPACITY /*= 0x8*/,

	/** Displacement texture<p>
	 *
	 *  The exact purpose and format is application-dependent.
    *  Higher color values stand for higher vertex displacements.
   */
   aiTextureType_DISPLACEMENT /*= 0x9*/,

	/** Lightmap texture (aka Ambient Occlusion)<p>
	 *
	 *  Both 'Lightmaps' and dedicated 'ambient occlusion maps' are
	 *  covered by this material property. The texture contains a
	 *  scaling value for the final color value of a pixel. Its
	 *  intensity is not affected by incoming light.
   */
   aiTextureType_LIGHTMAP /*= 0xA*/,

	/** Reflection texture<p>
	 *
	 * Contains the color of a perfect mirror reflection.
	 * Rarely used, almost never for real-time applications.
   */
   aiTextureType_REFLECTION /*= 0xB*/,

	/** Unknown texture<p>
	 *
	 *  A texture reference that does not match any of the definitions 
	 *  above is considered to be 'unknown'. It is still imported,
	 *  but is excluded from any further postprocessing.
   */
   aiTextureType_UNKNOWN /*= 0xC*/,
}
