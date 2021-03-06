import org.checkerframework.checker.nullness.qual.*;
import java.util.*;

class MyClass<@Nullable T> {
    T get() { throw new RuntimeException(); }

    void testPositive() {
        MyClass<@Nullable String> l = new MyClass<@Nullable String>();
        //:: error: (dereference.of.nullable)
        l.get().toString();
    }

    void testInvalidParam() {
        //:: error: (type.argument.type.incompatible)
        MyClass<@NonNull String> l;
    }
}
