package com.google.monitoring.metrics;

/**
 * A testing utility class that contains backports of useful but not yet released JUnit methods.
 *
 * <p>All of this code was taken directly from
 * https://github.com/junit-team/junit4/blob/a832c5afe5b0e7c2590d057a1a49a344d207f8a0/src/main/java/org/junit/Assert.java
 */
public class JUnitBackports {
  // TODO(b/68257761): Delete these and switch over to JUnit 4.13 methods upon release.

  /**
   * This interface facilitates the use of expectThrows from Java 8. It allows method references to
   * void methods (that declare checked exceptions) to be passed directly into expectThrows without
   * wrapping. It is not meant to be implemented directly.
   *
   * @since 4.13
   */
  public interface ThrowingRunnable {
    void run() throws Throwable;
  }

  /**
   * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
   * executed. If it does not throw an exception, an {@link AssertionError} is thrown. If it throws
   * the wrong type of exception, an {@code AssertionError} is thrown describing the mismatch; the
   * exception that was actually thrown can be obtained by calling {@link AssertionError#getCause}.
   *
   * @param expectedThrowable the expected type of the exception
   * @param runnable a function that is expected to throw an exception when executed
   * @since 4.13
   */
  public static void assertThrows(
      Class<? extends Throwable> expectedThrowable, ThrowingRunnable runnable) {
    expectThrows(expectedThrowable, runnable);
  }

  /**
   * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
   * executed. If it does, the exception object is returned. If it does not throw an exception, an
   * {@link AssertionError} is thrown. If it throws the wrong type of exception, an {@code
   * AssertionError} is thrown describing the mismatch; the exception that was actually thrown can
   * be obtained by calling {@link AssertionError#getCause}.
   *
   * @param expectedThrowable the expected type of the exception
   * @param runnable a function that is expected to throw an exception when executed
   * @return the exception thrown by {@code runnable}
   * @since 4.13
   */
  public static <T extends Throwable> T expectThrows(
      Class<T> expectedThrowable, ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable actualThrown) {
      if (expectedThrowable.isInstance(actualThrown)) {
        @SuppressWarnings("unchecked")
        T retVal = (T) actualThrown;
        return retVal;
      } else {
        String expected = formatClass(expectedThrowable);
        Class<? extends Throwable> actualThrowable = actualThrown.getClass();
        String actual = formatClass(actualThrowable);
        if (expected.equals(actual)) {
          // There must be multiple class loaders. Add the identity hash code so the message
          // doesn't say "expected: java.lang.String<my.package.MyException> ..."
          expected += "@" + Integer.toHexString(System.identityHashCode(expectedThrowable));
          actual += "@" + Integer.toHexString(System.identityHashCode(actualThrowable));
        }
        String mismatchMessage = format("unexpected exception type thrown;", expected, actual);

        // The AssertionError(String, Throwable) ctor is only available on JDK7.
        AssertionError assertionError = new AssertionError(mismatchMessage);
        assertionError.initCause(actualThrown);
        throw assertionError;
      }
    }
    String message =
        String.format(
            "expected %s to be thrown, but nothing was thrown", formatClass(expectedThrowable));
    throw new AssertionError(message);
  }

  static String format(String message, Object expected, Object actual) {
    String formatted = "";
    if (message != null && !"".equals(message)) {
      formatted = message + " ";
    }
    String expectedString = String.valueOf(expected);
    String actualString = String.valueOf(actual);
    if (equalsRegardingNull(expectedString, actualString)) {
      return formatted
          + "expected: "
          + formatClassAndValue(expected, expectedString)
          + " but was: "
          + formatClassAndValue(actual, actualString);
    } else {
      return formatted + "expected:<" + expectedString + "> but was:<" + actualString + ">";
    }
  }

  private static String formatClass(Class<?> value) {
    String className = value.getCanonicalName();
    return className == null ? value.getName() : className;
  }

  private static String formatClassAndValue(Object value, String valueString) {
    String className = value == null ? "null" : value.getClass().getName();
    return className + "<" + valueString + ">";
  }

  private static boolean equalsRegardingNull(Object expected, Object actual) {
    if (expected == null) {
      return actual == null;
    }

    return isEquals(expected, actual);
  }

  private static boolean isEquals(Object expected, Object actual) {
    return expected.equals(actual);
  }
}
