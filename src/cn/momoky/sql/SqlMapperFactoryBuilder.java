package cn.momoky.sql;

import cn.momoky.util.ClassScanner;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @ClassName SqlMapperFactoryBuilder
 * @Description TODO
 * @Author 钟智峰
 * @Date 2020/9/8 14:00
 * @Version 1.0
 */
public class SqlMapperFactoryBuilder {

    private  static final Map<String, SqlMapperFactory> factoryMap = new HashMap<>();


    public static synchronized SqlMapperFactory build(String propertiesName) {
        if (factoryMap.containsKey(propertiesName)) {
            return factoryMap.get(propertiesName);
        }
        SqlMapperFactory factory = null;
        try {
            factory = create(propertiesName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        factoryMap.put(propertiesName, factory);
        return factory;
    }

    private static SqlMapperFactory create(String propertiesName) throws IOException {
        InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesName);

        Properties properties = new Properties();
        properties.load(resource);

        String driver = properties.getProperty("driver");
        String url = properties.getProperty("url");
        String user = properties.getProperty("user");
        String password = properties.getProperty("password");
        String pkg = properties.getProperty("package");
        DataSource dataSource = new MDataSource(driver, url, user, password);

        List<Class<?>> classes = ClassScanner.scan(pkg);

        return new SqlMapperFactory(classes, dataSource);
    }

}
