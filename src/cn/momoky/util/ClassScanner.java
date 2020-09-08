package cn.momoky.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * @ClassName ClassScanner
 * @Description 扫描指定包下的所有类
 * @Author 钟智峰
 * @Date 2020/9/7 20:09
 * @Version 1.0
 */
public class ClassScanner {

    /**
     * @Description 判断当前类是否运行在jar包中
     * @return
     */
    public static boolean isRunJar() throws MalformedURLException {
        URL url = ClassScanner.class.getResource("");
        String u = url.toString();
        if (u.contains("sql-mapper.jar!")) {
            url = new URL(u.substring(4));
        }
        String protocol = url.getProtocol();
        return "jar".equals(protocol);
    }

    /**
     * @Description 扫描指定包下的所有类
     * @param pkg 包名
     * @return Class 对象 集合
     */
    public static List<Class<?>> scan(String pkg)  {
        List<String> list;
        List<Class<?>> classes = null;
        try {
            if (isRunJar()) {
                list = scanJar(pkg);
            } else {
                list = scanFile(pkg);
            }

            classes = loadClass(list);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return classes;
    }

    // 加载 *.class 文件
    private static List<Class<?>> loadClass(List<String> classFullNames) throws ClassNotFoundException {

        if (classFullNames.size() == 0) return null;
        List<Class<?>> classes = new ArrayList<>();
        for (String classFullName : classFullNames) {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(classFullName);
            classes.add(clazz);
        }
        return classes;
    }

    // 扫描普通文件
    private static List<String> scanFile(String pkg) {


        String path = ClassScanner.class.getResource("/").getPath();
        String pkgPath = pkg.replaceAll("\\.", "/");
        int prefixLen = path.length();
        File root = new File(path + pkgPath);

        if (!root.exists()) throw new RuntimeException(pkg + "包不存在");

        Stack<File> stack = new Stack<>();
        stack.push(root);

        List<String> classFullNames = new ArrayList<>();

        while (!stack.isEmpty()) {
            root = stack.pop();
            if (!root.isDirectory()) {
                String fileName = root.getAbsolutePath();
                if (fileName.endsWith(".class")) {
                    String classFullName = fileName.substring(prefixLen - 1, fileName.length() - 6)
                            .replaceAll("\\\\|/", ".");
                    classFullNames.add(classFullName);
                }
                continue;
            }
            File[] files = root.listFiles();
            if (files == null) continue;
            for (File f : files) {
                stack.push(f);
            }
        }
        return classFullNames;
    }

    // 扫描 jar 包
    private static List<String> scanJar(String pkg) throws IOException {
        String pkgPath = pkg.replaceAll("\\.", "/");
        List<String> classFullNames = new ArrayList<>();

        File root = new File(System.getProperty("user.dir"));
        File[] files = root.listFiles((dir, name) -> name.endsWith(".jar"));
        assert files != null;
        JarFile jarFile = new JarFile(files[0].getAbsolutePath());
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String jarEntryName = jarEntry.getName();
            if (jarEntryName.startsWith(pkgPath) && jarEntryName.endsWith(".class")) {
                String classFullName = jarEntryName.substring(0, jarEntryName.length() - 6)
                        .replaceAll("/", ".");
                classFullNames.add(classFullName);
            }
        }
        return classFullNames;
    }
}
