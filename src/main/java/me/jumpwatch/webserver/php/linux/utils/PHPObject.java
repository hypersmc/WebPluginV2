package me.jumpwatch.webserver.php.linux.utils;

import com.caucho.quercus.env.*;

/**
 * A thin wrapper around instances of com.caucho.quercus.env.Value, themselves representing
 * instances of PHP objects. This wrapper makes it easier to invoke methods and set and get properties.
 * @author Aaron Collegeman aaron@collegeman.net
 */
public class PHPObject {

    private Value wrapped;

    private Env env;

    public PHPObject(Env env, Value value) {
        this.env = env;
        this.wrapped = value;
    }

    /**
     * Invoke the method name on wrapped Value object, passing
     * into the invocation parameters args.
     * @return An instance of PHPObject, wrapping any return value of nameed method.
     */
    public final PHPObject invokeMethod(String name, Object ... args) {
        if (args != null && args.length > 0) {
            Value values[] = new Value[args.length];
            for(int i=0; i< args.length; i++)
                values[i] = PHP.toValue(env, args[i]);

            return new PHPObject(env, wrapped.callMethod(env, new StringBuilderValue(name), values));
        }
        else {
            return new PHPObject(env, wrapped.callMethod(env, new StringBuilderValue(name), new Value[]{}));
        }
    }

    /**
     * Set a public property of the wrapped Value to value.
     * @return This instance of PHPObject, to support method chaining.
     */
    public final PHPObject setProperty(String name, Object value) {
        wrapped.putField(env, new StringBuilderValue(name), PHP.toValue(env, value));
        return this;
    }

    /**
     * Retrieve a public property name of the wrapped Value.
     * @return A new instance of PHPObject, wrapping the retrieved property.
     */
    public final PHPObject getProperty(String name) {
        return new PHPObject(env, wrapped.getField(env, new StringBuilderValue(name)));
    }

    public Value getWrappedValue() {
        return wrapped;
    }

    public String toString() {
        return wrapped.toJavaString();
    }

}
