package me.jumpwatch.webserver.php.linux.utils;

import com.caucho.quercus.Quercus;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.QuercusClass;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.page.QuercusPage;
import com.caucho.vfs.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.*;
import java.util.logging.Logger;


public class PHP {

    private static final Logger log = Logger.getLogger(PHP.class.getName());
    private static Quercus quercus;

    private synchronized Quercus getQuercus() {
        if (quercus == null) {
            quercus = new Quercus();
        }
        return quercus;
    }

    public PHP(String url) {
        this(url, PHP.class.getClassLoader());
        System.out.println(url);
    }

    private QuercusPage main;


    public PHP() {}



    public PHP(String url, ClassLoader classLoader) {
        System.out.println(url);
        File ref = new File(url);
        initByFile(ref);
    }

    /**
     * Initialize a PHP wrapper with a specific File loaded by the host.
     * @param file A file full of PHP script
     */
    public PHP(File file) {
        initByFile(file);
    }

    private void initByInputStream(InputStream stream) {
        try {
            StringBuilder sourceCode = new StringBuilder(1024);
            BufferedReader in = new BufferedReader(new InputStreamReader(stream));
            char[] buffer = new char[1024];
            int read = 0;
            while ((read = in.read(buffer)) != -1)
                sourceCode.append(buffer, 0, read);
            in.close();

            snippet(sourceCode.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initByFile(File ref) {
        if (!ref.exists()) {
            throw new RuntimeException(new FileNotFoundException("No PHP file or directory at ["+ref.getAbsolutePath()+"]"));
        }

        if (ref.isDirectory()) {
            snippet("");
            getEnv().setPwd(new FilePath(ref.getAbsolutePath()));
        }
        else {
            try {
                main = getQuercus().parse(new FilePath(ref.getAbsolutePath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            initEnv(main);

            File dir = ref.getParentFile();
            if (dir != null)
                getEnv().setPwd(new FilePath(ref.getParentFile().getAbsolutePath()));

            main.executeTop(getEnv());
        }
    }

    private Env env;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private StreamImpl out;
    private WriteStream ws;

    private void initEnv(QuercusPage page) {
        if (env == null) {
            request = new MockHttpServletRequest();
            response = new MockHttpServletResponse();

            WriterStreamImpl writer = new WriterStreamImpl();
            try {
                writer.setWriter(response.getWriter());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            out = writer;
            ws = new WriteStream(out);
            ws.setNewlineString("\n");

            env = getQuercus().createEnv(page, ws, request, response);

            env.setPwd(new FilePath(System.getProperty("user.dir")));
            env.start();
        }
    }

    /**
     * Retrieves the Quercus execution environment, which your Java code
     * can use to interact directly with the Quercus parsing engine.
     * @link http://www.caucho.com/resin-javadoc/com/caucho/quercus/env/Env.html
     * @throws IllegalStateException When environment has not yet been initialized
     */
    public final Env getEnv() {
        if (env == null)
            throw new IllegalStateException("Environment not yet initialized");
        return env;
    }

    /**
     * Set the value of a global variable in the PHP execution environment.
     * @param name The name of the global parameter to create/update
     * @param obj The value to store there
     */
    public PHP set(String name, Object obj) {
        snippet("");
        getEnv().setGlobalValue(name, toValue(getEnv(), obj));
        return this;
    }

    /**
     * Retrieve the value of a global varialbe in the PHP execution environment.
     * @param name The name of the global parameter tto read
     */
    public PHPObject get(String name) {
        snippet("");
        return new PHPObject(getEnv(), getEnv().getGlobalValue(name));
    }

    /**
     * Ensures that obj is of type or wrapped in an instance
     * of Quercus' Value, with respect to the given execution
     * environment env.
     * @return obj or obj wrapped in a Value instance.
     */
    public static Value toValue(Env env, Object obj) {
        if (obj == null)
            return env.wrapJava(obj);
        else if (obj instanceof PHPObject)
            return ((PHPObject) obj).getWrappedValue();
        else if (obj instanceof Value)
            return (Value) obj;
        else
            return env.wrapJava(obj);
    }

    /**
     * Parse and execute a snippet of PHP script, adding to the
     * execution context any artifacts and/or output generated by the code.
     */
    public PHP snippet(String snippet) {
        try {
            QuercusPage page = getQuercus().parse(StringStream.open(snippet));
            initEnv(page);
            page.executeTop(getEnv());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    /**
     * Retrieve the output generated by all PHP scripts executed in this context.
     */
    public String toString() {
        if (env != null) {
            try {
                ws.flush();
                return response.getContentAsString();
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            return null;
        }
    }

    /**
     * Clear any text buffered by script execution, presumably in preparation for
     * executing another PHP snippet.
     * @return This instance of PHP, to support method chaining.
     */
    public PHP clear() {
        response.setCommitted(false);
        response.reset();
        return this;
    }

    /**
     * Call the PHP function named fxName with arguments arguments
     * @return An instance of PHPObject, wrapped around the return value of the function.
     */
    public PHPObject fx(String fxName, Object ... arguments) {
        if (arguments != null && arguments.length > 0) {
            Value[] values = new Value[arguments.length];
            for (int i=0; i < arguments.length; i++)
                values[i] = toValue(getEnv(), arguments[i]);

            return new PHPObject(getEnv(), getEnv().call(fxName, values));
        }
        else {
            return new PHPObject(getEnv(), getEnv().call(fxName));
        }
    }

    /**
     * Create a new instance of the PHP class className, initialized with
     * arguments arguments.
     * @return An instance of PHPObject, wrapping the new instance of className.
     */
    public PHPObject newInstance(String className, Object ... arguments) {
        QuercusClass clazz = getEnv().findClass(className);
        if (clazz == null)
            throw new RuntimeException(new ClassNotFoundException("PHP:"+className));

        if (arguments != null && arguments.length > 0) {
            Value[] values = new Value[arguments.length];
            for (int i=0; i < arguments.length; i++)
                values[i] = toValue(getEnv(), arguments[i]);

            return new PHPObject(getEnv(), clazz.callNew(getEnv(), values));
        }
        else {
            return new PHPObject(getEnv(), clazz.callNew(getEnv(), new Value[]{}));
        }
    }

}