package cn.momoky.annotation;


import java.lang.annotation.*;

@Documented
@Inherited
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String name();
}
