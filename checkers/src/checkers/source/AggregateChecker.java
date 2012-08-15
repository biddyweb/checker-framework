package checkers.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import com.sun.source.util.TreePath;

/**
 * An aggregate checker that packages multiple checkers together.  The
 * resulting checker invokes the individual checkers together on the processed
 * files.
 *
 * This class delegates {@code AbstractTypeProcessor} responsibilities to each
 * of the checkers.
 *
 * Checker writers need to subclass this class and only override
 * {@link #getSupportedCheckers()} to indicate the classes of the checkers
 * to be bundled.
 */
public abstract class AggregateChecker extends AbstractTypeProcessor {

    protected List<SourceChecker> checkers;

    /**
     * Returns the list of supported checkers to be run together.
     * Subclasses need to override this method.
     */
    protected abstract Collection<Class<? extends SourceChecker>> getSupportedCheckers();

    public AggregateChecker() {
        Collection<Class<? extends SourceChecker>> checkerClasses = getSupportedCheckers();

        checkers = new ArrayList<SourceChecker>(checkerClasses.size());
        for (Class<? extends SourceChecker> checkerClass : checkerClasses) {
            try {
                SourceChecker instance = checkerClass.newInstance();
                checkers.add(instance);
            } catch (Exception e) {
                System.err.println("Couldn't instantiate an instance of " + checkerClass);
            }
        }
    }

    @Override
    public final void init(ProcessingEnvironment env) {
        super.init(env);
        for (SourceChecker checker : checkers) {
            checker.setProcessingEnvironment(env);
        }
    }

    @Override
    public void typeProcessingStart() {
        super.typeProcessingStart();
        for (SourceChecker checker : checkers) {
            checker.typeProcessingStart();
        }
    }

    // AbstractTypeProcessor delegation
    @Override
    public final void typeProcess(TypeElement element, TreePath tree) {
        int errsOnLastExit = 0;
        for (SourceChecker checker : checkers) {
            checker.errsOnLastExit = errsOnLastExit;
            checker.typeProcess(element, tree);
            errsOnLastExit = checker.errsOnLastExit;
        }
    }

    @Override
    public void typeProcessingOver() {
        for (SourceChecker checker : checkers) {
            checker.typeProcessingOver();
        }
    }

    @Override
    public final Set<String> getSupportedOptions() {
        Set<String> options = new HashSet<String>();
        for (SourceChecker checker : checkers) {
            options.addAll(checker.getSupportedOptions());
        }
        return options;
    }

    @Override
    public final Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("*");
    }

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }
}