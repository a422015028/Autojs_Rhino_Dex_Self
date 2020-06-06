/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tools.jsc;

import cn.hutool.core.lang.Console;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.optimizer.ClassCompiler;
import org.mozilla.javascript.tools.SourceReader;
import org.mozilla.javascript.tools.ToolErrorReporter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Norris Boyd
 */
public class Main {

    /**
     *主入口点。
     *
     * 处理参数就像普通的Java程序一样。
     * 然后设置执行环境并开始 编译脚本
     */
    public static void main(String args[])
    {
       // args=new String[]{"-encoding", "UTF-8","E:/Work/GitHub/batchJs2Dex/other/aaa.js"};

        Console.log(args);
        Main main = new Main();
        args = main.processOptions(args);
        if (args == null) {
            if (main.printHelp) {
                System.out.println(ToolErrorReporter.getMessage(
                    "msg.jsc.usage", Main.class.getName()));
                System.exit(0);
            }
            System.exit(1);
        }
        if (!main.reporter.hasReportedError()) {
            main.processSource(args);
        }
    }

    public Main()
    {
        reporter = new ToolErrorReporter(true);
        compilerEnv = new CompilerEnvirons();
        compilerEnv.setErrorReporter(reporter);
        compiler = new ClassCompiler(compilerEnv);
    }

    /**
     * 解析参数。
     *
     */
    public String[] processOptions(String args[])
    {
        targetPackage = "";        // 默认为无包
        compilerEnv.setGenerateDebugInfo(false);   // 默认为无符号
        for (int i=0; i < args.length; i++) {
            String arg = args[i];
            System.out.println("参数"+i+":"+arg);
            if (!arg.startsWith("-")) {
                int tail = args.length - i;
                if (targetName != null && tail > 1) {
                    addError("msg.multiple.js.to.file", targetName);
                    return null;
                }
                String[] result = new String[tail];
                for (int j = 0; j != tail; ++j) {
                    result[j] = args[i + j];
                }
                return result;
            }
            if (arg.equals("-help") || arg.equals("-h")
                || arg.equals("--help"))
            {
                printHelp = true;
                return null;
            }

            try {
                if (arg.equals("-version") && ++i < args.length) {
                    int version = Integer.parseInt(args[i]);
                    compilerEnv.setLanguageVersion(version);
                    continue;
                }
                if ((arg.equals("-opt") || arg.equals("-O"))  &&
                    ++i < args.length)
                {
                    int optLevel = Integer.parseInt(args[i]);
                    compilerEnv.setOptimizationLevel(optLevel);
                    continue;
                }
            }
            catch (NumberFormatException e) {
                badUsage(args[i]);
                return null;
            }
            if (arg.equals("-nosource")) {
                compilerEnv.setGeneratingSource(false);
                continue;
            }
            if (arg.equals("-debug") || arg.equals("-g")) {
                compilerEnv.setGenerateDebugInfo(true);
                continue;
            }
            if (arg.equals("-main-method-class") && ++i < args.length) {
                compiler.setMainMethodClass(args[i]);
                continue;
            }
            if (arg.equals("-encoding") && ++i < args.length) {
                characterEncoding = args[i];
                continue;
            }
            if (arg.equals("-o") && ++i < args.length) {
                String name = args[i];
                int end = name.length();
                if (end == 0
                    || !Character.isJavaIdentifierStart(name.charAt(0)))
                {
                    addError("msg.invalid.classfile.name", name);
                    continue;
                }
                for (int j = 1; j < end; j++) {
                    char c = name.charAt(j);
                    if (!Character.isJavaIdentifierPart(c)) {
                        if (c == '.') {
                            // check if it is the dot in .class
                            if (j == end - 6 && name.endsWith(".class")) {
                                name = name.substring(0, j);
                                break;
                            }
                        }
                        addError("msg.invalid.classfile.name", name);
                        break;
                    }
                }
                targetName = name;
                continue;
            }
            if (arg.equals("-observe-instruction-count")) {
                compilerEnv.setGenerateObserverCount(true);
            }
            if (arg.equals("-package") && ++i < args.length) {
                String pkg = args[i];
                int end = pkg.length();
                for (int j = 0; j != end; ++j) {
                    char c = pkg.charAt(j);
                    if (Character.isJavaIdentifierStart(c)) {
                        for (++j; j != end; ++j) {
                            c = pkg.charAt(j);
                            if (!Character.isJavaIdentifierPart(c)) {
                                break;
                            }
                        }
                        if (j == end) {
                            break;
                        }
                        if (c == '.' && j != end - 1) {
                            continue;
                        }
                    }
                    addError("msg.package.name", targetPackage);
                    return null;
                }
                targetPackage = pkg;
                continue;
            }
            if (arg.equals("-extends") && ++i < args.length) {
                String targetExtends = args[i];
                Class<?> superClass;
                try {
                    superClass = Class.forName(targetExtends);
                } catch (ClassNotFoundException e) {
                    throw new Error(e.toString()); // TODO: better error
                }
                compiler.setTargetExtends(superClass);
                continue;
            }
            if (arg.equals("-implements") && ++i < args.length) {
                // TODO: allow for multiple comma-separated interfaces.
                String targetImplements = args[i];
                StringTokenizer st = new StringTokenizer(targetImplements,
                                                         ",");
                List<Class<?>> list = new ArrayList<Class<?>>();
                while (st.hasMoreTokens()) {
                    String className = st.nextToken();
                    try {
                        list.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        throw new Error(e.toString()); // TODO: better error
                    }
                }
                Class<?>[] implementsClasses = list.toArray(new Class<?>[list.size()]);
                compiler.setTargetImplements(implementsClasses);
                continue;
            }
            if (arg.equals("-d") && ++i < args.length) {
                destinationDir = args[i];
                continue;
            }
            badUsage(arg);
            return null;
        }
        // no file name
        p(ToolErrorReporter.getMessage("msg.no.file"));
        return null;
    }
    /**
     * 打印使用情况消息。
     */
    private static void badUsage(String s) {
        System.err.println(ToolErrorReporter.getMessage(
            "msg.jsc.bad.usage", Main.class.getName(), s));
    }

    /**
     * 编译JavaScript源。
     *
     */
    public void processSource(String[] filenames)
    {
        for (int i = 0; i != filenames.length; ++i) {
            String filename = filenames[i];

            if (!filename.endsWith(".js")) {
                //后缀名 不是 js
                addError("msg.文件后缀不是js", filename);
                return;
            }

            File f = new File(filename);

            //js 源码
            String source = readSource(f);
            System.out.println(source);
            if (source == null) return;

            String mainClassName = targetName;

            if (mainClassName == null) {
                //取文件名称作为 mainClassName
                String name = f.getName();
                String nojs = name.substring(0, name.length() - 3);
                mainClassName = getClassName(nojs);
            }

            if (targetPackage.length() != 0) {
                mainClassName = targetPackage+"."+mainClassName;
            }

            Console.log("编译之前的参数:1:"+filename+"2:"+mainClassName);
            Object[] compiled
                = compiler.compileToClassFiles(source, filename, 1,
                                               mainClassName);
            if (compiled == null || compiled.length == 0) {
                return;
            }

            //目标上级文件夹 (用于存放生成的 class文件)
            File targetTopDir = null;
            if (destinationDir != null) {
                targetTopDir = new File(destinationDir);
            } else {
                String parent = f.getParent();
                if (parent != null) {
                    targetTopDir = new File(parent);
                }
            }
            for (int j = 0; j != compiled.length; j += 2) {
                String className = (String)compiled[j];
                byte[] bytes = (byte[])compiled[j + 1];
                File outfile = getOutputFile(targetTopDir, className);
                try {
                    FileOutputStream os = new FileOutputStream(outfile);
                    try {
                        os.write(bytes);
                    } finally {
                        os.close();
                    }
                } catch (IOException ioe) {
                    addFormatedError(ioe.toString());
                }
            }
        }
    }

    /**
     * 读取js 源码
     * @param f
     * @return
     */
    private String readSource(File f)
    {
        String absPath = f.getAbsolutePath();
        if (!f.isFile()) {
            //没有找到 js文件
            addError("msg.jsfile.not.found", absPath);
            return null;
        }
        //强制 UTF-8
        characterEncoding = StandardCharsets.UTF_8.toString();

        try {
            return (String)SourceReader.readFileOrUrl(absPath, true, characterEncoding);
        } catch (FileNotFoundException ex) {
            addError("msg.couldnt.open", absPath);
        } catch (IOException ioe) {
            addFormatedError(ioe.toString());
        }
        return null;
    }

    /**
     * 获取输出的文件
     * @param parentDir
     * @param className
     * @return
     */
    private File getOutputFile(File parentDir, String className)
    {
        String path = className.replace('.', File.separatorChar);
        path = path.concat(".class");
        File f = new File(parentDir, path);
        String dirPath = f.getParent();
        if (dirPath != null) {
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        return f;
    }

    /**
     * 确认类文件名是合法的Java标识符。如果文件名不是以JavaLetter开头，则用
     *下划线替换非法字符，并在名称前加上下划线。
     */

    String getClassName(String name) {
        char[] s = new char[name.length()+1];
        char c;
        int j = 0;

        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            s[j++] = '_';
        }
        for (int i=0; i < name.length(); i++, j++) {
            c = name.charAt(i);
            if ( Character.isJavaIdentifierPart(c) ) {
                s[j] = c;
            } else {
                s[j] = '_';
            }
        }
        return (new String(s)).trim();
     }

    private static void p(String s) {
        System.out.println(s);
    }

    private void addError(String messageId, String arg)
    {
        String msg;
        if (arg == null) {
            msg = ToolErrorReporter.getMessage(messageId);
        } else {
            msg = ToolErrorReporter.getMessage(messageId, arg);
        }
        addFormatedError(msg);
    }

    /**
     * 添加格式化错误
     * @param message
     */
    private void addFormatedError(String message)
    {
        reporter.error(message, null, -1, null, -1);
    }

    /**
     * 是否 打印帮助
     */
    private boolean printHelp=true;

    private ToolErrorReporter reporter;
    /**
     * 编译器环境
     */
    private CompilerEnvirons compilerEnv;
    /**
     * 编译器
     */
    private ClassCompiler compiler;
    /**
     * 目标名称
     */
    private String targetName;
    /**
     * 目标包名
     */
    private String targetPackage;
    /**
     * 目标目录
     */
    private String destinationDir;
    /**
     * 字符编码
     */
    private String characterEncoding;
}

