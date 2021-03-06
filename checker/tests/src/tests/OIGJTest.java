package tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.checkerframework.framework.test.ParameterizedCheckerTest;
import org.junit.runners.Parameterized.Parameters;

/**
 * JUnit tests for the Interning Checker, which tests the Interned annotation.
 */
public class OIGJTest extends ParameterizedCheckerTest {

    public OIGJTest(File testFile) {
        super(testFile,
                org.checkerframework.checker.oigj.OIGJChecker.class,
                "oigj",
                "-Anomsgtext");
    }

    @Parameters
    public static Collection<Object[]> data() {
        return filter(testFiles("oigj", "all-systems"));
    }

    // Duplicate from JavariTest.
    protected static Collection<Object[]> filter(Collection<Object[]> in) {
        Collection<Object[]> out = new ArrayList<Object[]>();
        for (Object[] oa : in) {
            Collection<Object> oout = new LinkedList<Object>();
            for (Object o : oa) {
                if (!filter(o)) {
                    oout.add(o);
                }
            }
            if (!oout.isEmpty()) {
                out.add(oout.toArray());
            }
        }
        return out;
    }

    protected static boolean filter(Object o) {
        // TODO: Default qualifiers for this file seem wrong.
        return o.toString().equals("tests/all-systems/GenericsBounds.java") ||
                o.toString().equals("tests/all-systems/MethodTypeVars.java") ||
                o.toString().equals("tests/all-systems/Ternary.java") ||
                o.toString().equals("tests/all-systems/FieldWithInit.java") ||
                o.toString().equals("tests/all-systems/TypeVars.java") ||
                o.toString().equals("tests/all-systems/RawTypes.java") ||
                o.toString().equals("tests/all-systems/RawTypeAssignment.java") ||
                o.toString().equals("tests/all-systems/GenericsCasts.java") ||
                o.toString().equals("tests/all-systems/GenericsEnclosing.java") ||
                o.toString().equals("tests/all-systems/GenericTest12.java") ||
                o.toString().equals("tests/all-systems/Options.java") ||
                o.toString().equals("tests/all-systems/ForEach.java") ||
                o.toString().equals("tests/all-systems/Arrays.java") ||
                o.toString().equals("tests/all-systems/GenericTest11full.java") ||
                o.toString().equals("tests/all-systems/MissingBoundAnnotations.java") ||
                o.toString().equals("tests/all-systems/Enums.java");
    }
}
