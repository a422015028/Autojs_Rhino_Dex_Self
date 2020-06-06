package org.mozilla.mycode;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import com.itranswarp.compiler.JavaStringCompiler;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.optimizer.ClassCompiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

class Main
{

	static final String JAVA_SOURCE_CODE = "" +
			"package defpackage;" +
			"import org.mozilla.classfile.ByteCode;" +
			"import org.mozilla.classfile.ClassFileWriter;" +
			"public class StrUtils{" +
			"		public static String d(String data)" +
			"		{" +
			"			int l = data.length() / 2;" +
			"			byte[] b = new byte[l];" +
			"			for (int i = 0; i < l; i++)" +
			"			{" +
			"				b[i] = Integer.valueOf(data.substring(i * 2, (i * 2) + 2), 16).byteValue();" +
			"			}" +
			"			for (int i2 = 0; i2 < b.length; i2++)" +
			"			{" +
			"				b[i2] = (byte) (b[i2] - 1);" +
			"			}" +
			"			return new String(b);" +
			"		}" +
			"	}	";

	public static void main(String[] args) throws Exception
	{
		String filePath = "E:\\Desktop\\RhinoScript\\HuLi2.js";
		toClassFile(FileUtil.readUtf8String(filePath));
	}

	public static String BASE_DIR_PATH = "E:\\Desktop\\RhinoScript\\" + getTimeString() + "\\";

	public static String getTimeString()
	{
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String time = formatter.format(date);
		return time;
	}

	//这里注意传参是 js字符串，不是js路径
	static void toClassFile(String script) throws Exception
	{
		//创建编译环境
		CompilerEnvirons compilerEnv = new CompilerEnvirons();
		ClassCompiler compiler = new ClassCompiler(compilerEnv);
		//compileToClassFiles的第3个参数比较重要，它表明了js转成.class的类路径，影响到调用的方法
		Object[] compiled = compiler.compileToClassFiles(
				script,
				null,
				1,
				"aaa");

		for (int j = 0; j != compiled.length; j += 2)
		{
			//String className = (String) compiled[j];

			JavaStringCompiler compiler2 = new JavaStringCompiler();
			Map<String, byte[]> results = compiler2.compile("StrUtils.java", JAVA_SOURCE_CODE);

			Console.log(results);
			//解密帮助类的数据
			byte[] utilsBytes = results.get("defpackage.StrUtils");

			String utilsClassPath = BASE_DIR_PATH + "defpackage//StrUtils.class";
			File utilsFile = new File(utilsClassPath);
			utilsFile.getParentFile().mkdirs();

			try (FileOutputStream fos = new FileOutputStream(utilsFile))
			{
				fos.write(utilsBytes);
			}
			catch (FileNotFoundException e)
			{
				System.out.println("utils 文件未找到！");
				e.printStackTrace();
			}
			catch (IOException e)
			{
				System.out.println("utils 文件保存失败！");
				e.printStackTrace();
			}


			String classPath = BASE_DIR_PATH + "aaa.class";

			//js 转为java 后的 数据
			byte[] bytes = (byte[]) compiled[(j + 1)];
			File file = new File(classPath);
			file.getParentFile().mkdirs();
			try (FileOutputStream fos = new FileOutputStream(file))
			{
				fos.write(bytes);
			}
			catch (FileNotFoundException e)
			{
				System.out.println("文件未找到！");
				e.printStackTrace();
			}
			catch (IOException e)
			{
				System.out.println("文件保存失败！");
				e.printStackTrace();
			}

			cmdExec("jar cvf demo.jar *");

			System.out.println(utilsFile.exists());
			String aaa = "java -jar E:\\Software\\androidstudioSDK\\build-tools\\29.0.3\\lib\\dx.jar --dex " +
					"--output=aaa.dex " +
					"demo.jar";

			cmdExec(aaa);

		}
		System.out.println("编译成功！");
	}

	public static byte[] byteMerger(byte[] bt1, byte[] bt2)
	{
		byte[] bt3 = new byte[bt1.length + bt2.length];
		System.arraycopy(bt1, 0, bt3, 0, bt1.length);
		System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
		return bt3;
	}

	public static void cmdExec(String cmd)
	{

		Runtime run = Runtime.getRuntime();
		try
		{
			Process p = run.exec(cmd, new String[]{}, new File(BASE_DIR_PATH));
			InputStream ins = p.getInputStream();
			InputStream ers = p.getErrorStream();
			new Thread(new inputStreamThread(ins)).start();
			p.waitFor();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	static class inputStreamThread implements Runnable
	{
		private InputStream ins = null;
		private BufferedReader bfr = null;

		public inputStreamThread(InputStream ins)
		{
			this.ins = ins;
			this.bfr = new BufferedReader(new InputStreamReader(ins));
		}

		@Override
		public void run()
		{
			String line = null;
			byte[] b = new byte[100];
			int num = 0;
			try
			{
				while ((num = ins.read(b)) != -1)
				{
					System.out.println(new String(b, "gb2312"));
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

}