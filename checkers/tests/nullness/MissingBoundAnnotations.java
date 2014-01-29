import java.util.*;
import checkers.quals.*;
import checkers.nullness.quals.*;

public final class MissingBoundAnnotations {

  // Test that the upper bound "Comparable<...>" receives an @Unqualified annotation.
  // Otherwise, it fails the check for empty annotations in GraphQualifierHierarchy.
  public static <K extends Comparable<? super K>,V> Collection<@KeyFor("#1") K> sortedKeySet(Map<K,V> m) {
    ArrayList<@KeyFor("#1") K> theKeys = new ArrayList<@KeyFor("#1") K> (m.keySet());
    Collections.sort (theKeys);
    return theKeys;
  }

}
