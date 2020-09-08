package cn.momoky.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName BeanUtil
 * @Description TODO
 * @Author 钟智峰
 * @Date 2020/9/5 16:32
 * @Version 1.0
 */
public class BeanUtil {

    /**
     * @Description 将 字段 - 值的映射 按照对象属性名封装成对象
     * @param map 字段 - 值 映射
     * @param clazz 目标对象的字节码
     * @param <T> 泛型方法
     * @return  返回 clazz 对应的对象
     */
    public static <T> T objectForMap(Map<String, Object> map, Class<T> clazz) {
        T instance = null;
        try {
            instance = clazz.newInstance();
            for (String fieldName: map.keySet()) {
                Field field = clazz.getDeclaredField(fieldName);
                Class<?> type = field.getType();
                Method setter = clazz.getDeclaredMethod("set" + upperFirstLetter(fieldName), type);
                setter.invoke(instance, map.get(fieldName));
            }
        } catch (InstantiationException | IllegalAccessException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return instance;
    }

    /**
     * @Description 将对象按照属性名转换成 属性名-值 的Map
     * @param o 需要转换的对象
     * @param <T> 泛型方法
     * @return 属性名-值 的映射
     */
    public static <T> Map<String, Object> mapForObject(T o) {
        Class<?> clazz = o.getClass();
        Map<String, Object> map = new HashMap<>();

        Field[] fields = clazz.getDeclaredFields();

        for (Field f : fields) {
            String key = f.getName();
            Class<?> type = f.getType();

            try {
                if (type.isAssignableFrom(boolean.class)
                        || type.isAssignableFrom(Boolean.class)) {
                    Method getter = clazz.getDeclaredMethod("is" + upperFirstLetter(key));
                    map.put(key, getter.invoke(o));
                } else {
                    Method getter = clazz.getDeclaredMethod("get" + upperFirstLetter(key));
                    map.put(key, getter.invoke(o));
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return map;
    }

    /**
     * @Description 根据属性名获取属性值
     * @param o
     * @param clazz 对象字节码
     * @param fieldName 属性名
     * @param <T>
     * @return 属性值
     */
    public static <T> Object getValue(Object o, Class<T> clazz,String fieldName) {

        Object value = null;
        try {
            Field field = clazz.getDeclaredField(fieldName);

            Class<?> type = field.getType();
            Method getter = null;
            if (type.isAssignableFrom(boolean.class)
                    || type.isAssignableFrom(Boolean.class)) {
                getter = clazz.getDeclaredMethod("is" + upperFirstLetter(fieldName));
            } else {
                getter = clazz.getDeclaredMethod("get" + upperFirstLetter(fieldName));
            }
            value = getter.invoke(o);
        } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return value;
    }

    /**
     * @Description 将开头字母大写
     * @param str
     * @return
     */
    private static String upperFirstLetter(String str) {
        return str.substring(0, 1).toUpperCase()+str.substring(1);
    }

}
