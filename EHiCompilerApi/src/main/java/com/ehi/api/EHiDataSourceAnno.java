package com.ehi.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用来标识一个 DataSource 层的注解,最后都会被整合到一个最终的 DataSource 中
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface EHiDataSourceAnno {

    /**
     * 生成的方法的前缀,用于区分
     *
     * @return
     */
    String value() default "";

    /**
     * 用于内部区分,不能为相同的
     *
     * @return
     */
    String uniqueCode();

    /**
     * 实现类,会被加入到缓存中
     */
    String impl() default "";

    /**
     * 如果实现类不是直接创建的,那么传入调用的方式
     *
     * @return
     */
    String callPath() default "";

}
