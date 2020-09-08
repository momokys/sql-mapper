package cn.momoky.sql;

import cn.momoky.annotation.*;
import cn.momoky.util.BeanUtil;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @ClassName SqlMapper
 * @Description TODO
 * @Author 钟智峰
 * @Date 2020/9/5 17:00
 * @Version 1.0
 */
public class SqlMapperFactory {

    private final Map<String, Object> proxyMap = new HashMap<>();
    private final Map<String, MethodInfo> methodMap = new HashMap<>();

    private final DataSource dataSource;

    SqlMapperFactory(List<Class<?>> classes, DataSource dataSource) {
        this.dataSource = dataSource;
        for (Class<?> clazz : classes) {
            if (!clazz.isAnnotationPresent(Mapper.class)) continue;
            createMapper(clazz);
        }
    }

    public <T> T getMapper(Class<T> clazz) {
        return (T) proxyMap.get(clazz.getName());
    }

    public <T> T getMapper(String className) {
        return (T) proxyMap.get(className);
    }

    private <T> void createMapper(Class<T> clazz) {

        parseMethod(clazz);

        Object mapper = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (Object.class.equals(method.getDeclaringClass())) {
                    return method.invoke(this, args);
                }
                return execute(clazz.getName() + "." + method.getName(), args);
            }
        });
        proxyMap.put(clazz.getName(), mapper);
    }

    private void parseMethod(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();

        for (Method method : methods) {
            MethodInfo methodInfo = new MethodInfo();

            methodInfo.setParam(method.getParameters());

            methodInfo.returnType = method.getReturnType();
            methodInfo.genericType = parseGeneric(method);

            String sql = null;
            if (method.isAnnotationPresent(Insert.class)) {
                methodInfo.setSqlType(SqlType.Insert);
                Insert insert = method.getAnnotation(Insert.class);
                sql = insert.sql();
                methodInfo.generatedKey = insert.generatedKey();
            } else if (method.isAnnotationPresent(Delete.class)) {
                methodInfo.setSqlType(SqlType.Delete);
                Delete delete = method.getAnnotation(Delete.class);
                sql = delete.sql();
            } else if (method.isAnnotationPresent(Update.class)) {
                methodInfo.setSqlType(SqlType.Update);
                Update update = method.getAnnotation(Update.class);
                sql = update.sql();
            } else if (method.isAnnotationPresent(Query.class)) {
                methodInfo.setSqlType(SqlType.Query);
                Query query = method.getAnnotation(Query.class);
                sql = query.sql();
            } else {
                throw new RuntimeException("无法代理此方法!!!");
            }

            methodInfo.sql = sql.replaceAll("#\\{.*?\\}", "?");
            methodInfo.tags = parseSql(sql);

            methodMap.put(clazz.getName()+"."+method.getName(), methodInfo);
        }
    }

    private List<String> parseSql(String sql) {
        Pattern reg = Pattern.compile("#\\{(.*?)\\}");
        Matcher matcher = reg.matcher(sql);

        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        return tags;
    }

    private String[] parseTag(String tag) {
        return tag.split("\\.");
    }

    private static  <T> Class<?> parseGeneric(Method method) {
        Class<?> returnType = method.getReturnType();

        if (returnType.isAssignableFrom(void.class)
                || returnType.isAssignableFrom(Map.class)
                || !returnType.isAssignableFrom(List.class)) {
            return null;
        }

        ParameterizedType type = (ParameterizedType) method.getGenericReturnType();

        Type[] types = type.getActualTypeArguments();

        if (types[0].getTypeName().startsWith("java.util.Map")) {
            return Map.class;
        }

        Class<?> clazz = null;
        try {
            clazz = Class.forName(types[0].getTypeName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return clazz;
    }

    private boolean resultSetEmpty(ResultSet rs) throws SQLException {
        rs.last();
        if (rs.getRow() == 0) {
            rs.first();
            return true;
        }
        rs.first();
        return false;
    }

    private void resultSetToMap(Map<String, Object> map, ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount;i++) {
            map.put(metaData.getColumnLabel(i), rs.getObject(i));
        }
    }

    private Object resultSetToObject(Class<?> clazz, ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        resultSetToMap(map, rs);
        return BeanUtil.objectForMap(map, clazz);
    }

    private void resultSetToListMap(List<Map<String, Object>> list, ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        rs.absolute(0);
        while (rs.next()) {
            Map<String, Object> map = new HashMap<>();
            for (int i = 1;i <= columnCount;i++) {
                resultSetToMap(map, rs);
            }
            list.add(map);
        }
    }

    private void resultSetToListBean(List<Object> list, Class<?> clazz, ResultSet rs) throws SQLException {
        List<Map<String, Object>> mapList = new ArrayList<>();
        resultSetToListMap(mapList, rs);

        for (Map<String, Object> map : mapList) {
            list.add(BeanUtil.objectForMap(map, clazz));
        }
    }

    private void resultSetToListBase(List<Object> list, ResultSet rs) throws SQLException {
        rs.absolute(0);
        while (rs.next()) {
            list.add(rs.getObject(1));
        }
    }

    private boolean isBaseType(Class<?> clazz) {
        return clazz.isAssignableFrom(Byte.class)
                || clazz.isAssignableFrom(Short.class)
                || clazz.isAssignableFrom(Integer.class)
                || clazz.isAssignableFrom(Long.class)
                || clazz.isAssignableFrom(Float.class)
                || clazz.isAssignableFrom(Double.class)
                || clazz.isAssignableFrom(Character.class)
                || clazz.isAssignableFrom(Boolean.class)
                || clazz.isAssignableFrom(String.class)
                || clazz.isAssignableFrom(Date.class);
    }

    private Object execute(String methodName ,Object[] args) {

        Object ret = null;

        MethodInfo methodInfo = methodMap.get(methodName);

        //System.out.println(methodInfo.getSql());

        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            if (methodInfo.generatedKey) pst = conn.prepareStatement(methodInfo.getSql(), Statement.RETURN_GENERATED_KEYS);
            else pst = conn.prepareStatement(methodInfo.getSql());
            for (int i = 0;i < methodInfo.tags.size();i++) {
                String[] p = parseTag(methodInfo.tags.get(i));
                Class<?> paramType = methodInfo.getParamType(p[0]);

                int paramIndex = methodInfo.getParamIndex(p[0]);
                Object value = null;
                if (p.length > 1) {
                    value = BeanUtil.getValue(args[paramIndex], paramType, p[1]);
                } else {
                    value = args[paramIndex];
                }
                pst.setObject(i+1, value);
            }

            if (methodInfo.getSqlType() == SqlType.Insert) {
                ret = pst.executeUpdate();
                if (methodInfo.returnType != null || methodInfo.generatedKey) {
                    ResultSet keys = pst.getGeneratedKeys();
                    if (keys.next()) ret = keys.getObject(1);
                }
            } else if (methodInfo.getSqlType() == SqlType.Query) {
                rs = pst.executeQuery();

                if (methodInfo.returnType != null) {
                    if (methodInfo.returnType.isAssignableFrom(List.class)) {
                        if (methodInfo.genericType.isAssignableFrom(Map.class)) {
                            List<Map<String, Object>> list = new ArrayList<>();
                            if(!resultSetEmpty(rs)) resultSetToListMap(list, rs);
                            ret = list;
                        } else if (isBaseType(methodInfo.genericType)) {
                            List<Object> list = new ArrayList<>();
                            if(!resultSetEmpty(rs)) resultSetToListBase(list, rs);
                            ret = list;
                        }else {
                            List<Object> list = new ArrayList<>();
                            if(!resultSetEmpty(rs)) resultSetToListBean(list, methodInfo.genericType, rs);
                            ret = list;
                        }
                    } else if (methodInfo.returnType.isAssignableFrom(Map.class)) {
                        Map<String, Object> map = new HashMap<>();
                        if(!resultSetEmpty(rs)) resultSetToMap(map, rs);
                        ret = map;
                    } else {
                        if (!resultSetEmpty(rs)) {
                            if (isBaseType(methodInfo.returnType)) {
                                rs.absolute(0);
                                if (rs.next()) {
                                    ret = rs.getObject(1);
                                }
                            } else {
                                ret = resultSetToObject(methodInfo.returnType, rs);
                            }
                        }
                    }
                }
                rs.close();
            } else {
                ret = pst.executeUpdate();
            }
            pst.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return ret;
    }

    private static class MethodInfo {

        private SqlType sqlType;

        private String sql;
        private List<String> tags;

        private final Map<String, Class<?>> paramMap = new HashMap<>();
        private final Map<String, Integer> paramIndex = new HashMap<>();
        private Class<?> returnType;

        private Class<?> genericType = null;

        private boolean generatedKey = false;

        public MethodInfo() {
        }

        public String getSql() {
            return sql;
        }

        public void setSqlType(SqlType type) {
            this.sqlType = type;
        }

        public SqlType getSqlType() {
            return sqlType;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public void setParam(Parameter[] parameters) {
            int index = 0;
            for (Parameter p : parameters) {
                String paramName = null;
                if (p.isAnnotationPresent(Param.class)) {
                    Param param = p.getAnnotation(Param.class);
                    paramName = param.name();
                } else {
                    paramName = p.getName();
                }
                paramIndex.put(paramName, index++);
                paramMap.put(paramName, p.getType());
            }
        }

        public Class<?> getParamType(String paramName) {
            return paramMap.get(paramName);
        }

        public int getParamIndex(String paramName) {
            if (!paramIndex.containsKey(paramName)) throw new RuntimeException(paramName+ " tag is not contains!!!");
            return paramIndex.get(paramName);
        }
    }
}
