package jet.opengl.postprocessing.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by mazhen'gui on 2017/4/1.
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention( RetentionPolicy.SOURCE)
public @interface CachaRes {
}
