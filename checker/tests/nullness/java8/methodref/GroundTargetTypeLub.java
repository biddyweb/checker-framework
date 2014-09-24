
import org.checkerframework.checker.nullness.qual.*;

interface Supplier <T extends @NonNull Object> {
    T supply();
}

class GroundTargetType {
    //:: error: (methodref.return.invalid)
    Supplier<? extends @Nullable Object> fn = GroundTargetType::myMethod;

    static @Nullable Object myMethod() { return null; }
}