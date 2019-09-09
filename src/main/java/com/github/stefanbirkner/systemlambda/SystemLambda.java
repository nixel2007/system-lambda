package com.github.stefanbirkner.systemlambda;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Properties;

import static java.lang.System.*;

/**
 * {@code SystemLambda} is a collection of functions for testing code
 * that uses {@code java.lang.System}.
 *
 * <h2>Security Manager</h2>
 *
 * <p>The function
 * {@link #withSecurityManager(SecurityManager, Statement) withSecurityManager}
 * lets you specify which {@code SecurityManager} is returned by
 * {@code System.getSecurityManger()} while your code under test is executed.
 * <pre>
 * &#064;Test
 * void execute_code_with_specific_SecurityManager() {
 *   SecurityManager securityManager = new ASecurityManager();
 *   withSecurityManager(
 *     securityManager,
 *     () -&gt; {
 *       //code under test
 *       //e.g. the following assertion is met
 *       assertSame(
 *         securityManager,
 *         System.getSecurityManager()
 *       );
 *     }
 *   );
 * }
 * </pre>
 * <p>After the statement {@code withSecurityManager(...)} is executed
 * {@code System.getSecurityManager()} will return the original security manager
 * again.
 *
 * <h2>System Properties</h2>
 *
 * <p>The function
 * {@link #restoreSystemProperties(Statement) restoreSystemProperties}
 * guarantees that after executing the test code each System property has the
 * same value like before. Therefore you can modify System properties inside of
 * the test code without having an impact on other tests.
 * <pre>
 * &#064;Test
 * void execute_code_that_manipulates_system_properties() {
 *   restoreSystemProperties(
 *     () -&gt; {
 *       System.setProperty("some.property", "some value");
 *       //code under test that reads properties (e.g. "some.property") or
 *       //modifies them.
 *     }
 *   );
 * }
 * </pre>
 *
 * <h2>System.in, System.out and System.err</h2>
 * <p>Command-line applications usually write to the console. If you write such
 * applications you need to test the output of these applications. The methods
 * {@link #tapSystemErr(Statement) tapSystemErr},
 * {@link #tapSystemErrNormalized(Statement) tapSystemErrNormalized},
 * {@link #tapSystemOut(Statement) tapSystemOut} and
 * {@link #tapSystemOutNormalized(Statement) tapSystemOutNormalized} allow you
 * to tap the text that is written to {@code System.err}/{@code System.out}. The
 * methods with the suffix {@code Normalized} normalize line breaks to
 * {@code \n} so that you can run tests with the same assertions on Linux,
 * macOS and Windows.
 *
 * <pre>
 * &#064;Test
 * void check_text_written_to_System_err() throws Exception {
 *   String text = tapSystemErr(
 *     () -&gt; System.err.println("some text")
 *   );
 *   assertEquals(text, "some text");
 * }
 *
 * &#064;Test
 * void check_multiple_lines_written_to_System_err() throws Exception {
 *   String text = tapSystemErrNormalized(
 *     () -&gt; {
 *       System.err.println("first line");
 *       System.err.println("second line");
 *     }
 *   );
 *   assertEquals(text, "first line\nsecond line");
 * }
 *
 * &#064;Test
 * void check_text_written_to_System_out() throws Exception {
 *   String text = tapSystemOut(
 *     () -&gt; System.out.println("some text")
 *   );
 *   assertEquals(text, "some text");
 * }
 *
 * &#064;Test
 * void check_multiple_lines_written_to_System_out() throws Exception {
 *   String text = tapSystemOutNormalized(
 *     () -&gt; {
 *       System.out.println("first line");
 *       System.out.println("second line");
 *     }
 *   );
 *   assertEquals(text, "first line\nsecond line");
 * }</pre>
 *
 * <p>You can assert that nothing is written to
 * {@code System.err}/{@code System.out} by wrapping code with the function
 * {@link #assertNothingWrittenToSystemErr(Statement)
 * assertNothingWrittenToSystemErr}/{@link #assertNothingWrittenToSystemOut(Statement)
 * assertNothingWrittenToSystemOut}. E.g. the following tests fail:
 * <pre>
 * &#064;Test
 * void fails_because_something_is_written_to_System_err() {
 *   assertNothingWrittenToSystemErr(
 *     () -&gt; {
 *        System.err.println("some text");
 *     }
 *   );
 * }
 *
 * &#064;Test
 * void fails_because_something_is_written_to_System_out() {
 *   assertNothingWrittenToSystemOut(
 *     () -&gt; {
 *        System.out.println("some text");
 *     }
 *   );
 * }
 * </pre>
 *
 * <p>If the code under test writes text to
 * {@code System.err}/{@code System.out} then it is intermixed with the output
 * of your build tool. Therefore you may want to avoid that the code under test
 * writes to {@code System.err}/{@code System.out}. You can achieve this with
 * the function {@link #muteSystemErr(Statement)
 * muteSystemErr}/{@link #muteSystemOut(Statement) muteSystemOut}. E.g. the
 * following tests don't write anything to
 * {@code System.err}/{@code System.out}:
 * <pre>
 * &#064;Test
 * void nothing_is_written_to_System_err() {
 *   muteSystemErr(
 *     () -&gt; {
 *        System.err.println("some text");
 *     }
 *   );
 * }
 *
 * &#064;Test
 * void nothing_is_written_to_System_out() {
 *   muteSystemOut(
 *     () -&gt; {
 *        System.out.println("some text");
 *     }
 *   );
 * }
 * </pre>
 */
public class SystemLambda {

	private static final boolean AUTO_FLUSH = true;
	private static final String DEFAULT_ENCODING = Charset.defaultCharset().name();

	/**
	 * Executes the statement and fails (throws an {@code AssertionError}) if
	 * the statement tries to write to {@code System.err}.
	 * <p>The following test fails
	 * <pre>
	 * &#064;Test
	 * public void fails_because_something_is_written_to_System_err() {
	 *   assertNothingWrittenToSystemErr(
	 *     () -&gt; {
	 *       System.err.println("some text");
	 *     }
	 *   );
	 * }
	 * </pre>
	 * The test fails with the failure "Tried to write 's' to System.err
	 * although this is not allowed."
	 *
	 * @param statement an arbitrary piece of code.
	 * @throws Exception any exception thrown by the statement or an
	 *                   {@code AssertionError} if the statement tries to write
	 *                   to {@code System.err}.
	 * @see #assertNothingWrittenToSystemOut(Statement)
	 * @since 1.0.0
	 */
	public static void assertNothingWrittenToSystemErr(
		Statement statement
	) throws Exception {
		executeWithSystemErrReplacement(
			new DisallowWriteStream(),
			statement
		);
	}

	/**
	 * Executes the statement and fails (throws an {@code AssertionError}) if
	 * the statement tries to write to {@code System.out}.
	 * <p>The following test fails
	 * <pre>
	 * &#064;Test
	 * public void fails_because_something_is_written_to_System_out() {
	 *   assertNothingWrittenToSystemOut(
	 *     () -&gt; {
	 *       System.out.println("some text");
	 *     }
	 *   );
	 * }
	 * </pre>
	 * The test fails with the failure "Tried to write 's' to System.out
	 * although this is not allowed."
	 *
	 * @param statement an arbitrary piece of code.
	 * @throws Exception any exception thrown by the statement or an
	 *                   {@code AssertionError} if the statement tries to write
	 *                   to {@code System.out}.
	 * @see #assertNothingWrittenToSystemErr(Statement)
	 * @since 1.0.0
	 */
	public static void assertNothingWrittenToSystemOut(
		Statement statement
	) throws Exception {
		executeWithSystemOutReplacement(
			new DisallowWriteStream(),
			statement
		);
	}

	/**
	 * Usually the output of a test to {@code System.err} does not have to be
	 * visible. It may even slowdown the test. {@code muteSystemErr} can be
	 * used to suppress this output.
	 * <pre>
	 * &#064;Test
	 * public void nothing_is_written_to_System_err() {
	 *   muteSystemErr(
	 *     () -&gt; {
	 *       System.err.println("some text");
	 *     }
	 *   );
	 * }
	 * </pre>
	 *
	 * @param statement an arbitrary piece of code.
	 * @throws Exception any exception thrown by the statement.
	 * @see #muteSystemOut(Statement)
	 * @since 1.0.0
	 */
	public static void muteSystemErr(
		Statement statement
	) throws Exception {
		executeWithSystemErrReplacement(
			new NoopStream(),
			statement
		);
	}

	/**
	 * Usually the output of a test to {@code System.out} does not have to be
	 * visible. It may even slowdown the test. {@code muteSystemOut} can be
	 * used to suppress this output.
	 * <pre>
	 * &#064;Test
	 * public void nothing_is_written_to_System_out() {
	 *   muteSystemOut(
	 *     () -&gt; {
	 *       System.out.println("some text");
	 *     }
	 *   );
	 * }
	 * </pre>
	 *
	 * @param statement an arbitrary piece of code.
	 * @throws Exception any exception thrown by the statement.
	 * @see #muteSystemErr(Statement)
	 * @since 1.0.0
	 */
	public static void muteSystemOut(
		Statement statement
	) throws Exception {
		executeWithSystemOutReplacement(
			new NoopStream(),
			statement
		);
	}

	/**
	 * {@code tapSystemErr} returns a String with the text that is written to
	 * {@code System.err} by the provided piece of code.
	 * <pre>
	 * &#064;Test
	 * public void check_the_text_that_is_written_to_System_err() {
	 *   String textWrittenToSystemErr = tapSystemErr(
	 *     () -&gt; {
	 *       System.err.print("some text");
	 *     }
	 *   );
	 *   assertEquals("some text", textWrittenToSystemErr);
	 * }
	 * </pre>
	 *
	 * @param statement an arbitrary piece of code.
	 * @return text that is written to {@code System.err} by the statement.
	 * @throws Exception any exception thrown by the statement.
	 * @see #tapSystemOut(Statement)
	 * @since 1.0.0
	 */
	public static String tapSystemErr(
		Statement statement
	) throws Exception {
		TapStream tapStream = new TapStream();
		executeWithSystemErrReplacement(
			tapStream,
			statement
		);
		return tapStream.textThatWasWritten();
	}

	/**
	 * {@code tapSystemOut} returns a String with the text that is written to
	 * {@code System.out} by the provided piece of code.
	 * <pre>
	 * &#064;Test
	 * public void check_the_text_that_is_written_to_System_out() {
	 *   String textWrittenToSystemOut = tapSystemOut(
	 *     () -&gt; {
	 *       System.out.print("some text");
	 *     }
	 *   );
	 *   assertEquals("some text", textWrittenToSystemOut);
	 * }
	 * </pre>
	 *
	 * @param statement an arbitrary piece of code.
	 * @return text that is written to {@code System.out} by the statement.
	 * @throws Exception any exception thrown by the statement.
	 * @see #tapSystemErr(Statement)
	 * @since 1.0.0
	 */
	public static String tapSystemOut(
		Statement statement
	) throws Exception {
		TapStream tapStream = new TapStream();
		executeWithSystemOutReplacement(
			tapStream,
			statement
		);
		return tapStream.textThatWasWritten();
	}

	/**
	 * {@code tapSystemErrNormalized} returns a String with the text that is
	 * written to {@code System.err} by the provided piece of code. New line
	 * characters are replaced with a single {@code \n}.
	 * <pre>
	 * &#064;Test
	 * public void check_the_text_that_is_written_to_System_err() {
	 *   String textWrittenToSystemErr = tapSystemErrNormalized(
	 *     () -&gt; {
	 *       System.err.println("some text");
	 *     }
	 *   );
	 *   assertEquals("some text\n", textWrittenToSystemErr);
	 * }
	 * </pre>
	 *
	 * @param statement an arbitrary piece of code.
	 * @return text that is written to {@code System.err} by the statement.
	 * @throws Exception any exception thrown by the statement.
	 * @see #tapSystemOut(Statement)
	 * @since 1.0.0
	 */
	public static String tapSystemErrNormalized(
		Statement statement
	) throws Exception {
		return tapSystemErr(statement)
			.replace(lineSeparator(), "\n");
	}

	/**
	 * {@code tapSystemOutNormalized} returns a String with the text that is
	 * written to {@code System.out} by the provided piece of code. New line
	 * characters are replaced with a single {@code \n}.
	 * <pre>
	 * &#064;Test
	 * public void check_the_text_that_is_written_to_System_out() {
	 *   String textWrittenToSystemOut = tapSystemOutNormalized(
	 *     () -&gt; {
	 *       System.out.println("some text");
	 *     }
	 *   );
	 *   assertEquals("some text\n", textWrittenToSystemOut);
	 * }
	 * </pre>
	 *
	 * @param statement an arbitrary piece of code.
	 * @return text that is written to {@code System.out} by the statement.
	 * @throws Exception any exception thrown by the statement.
	 * @see #tapSystemErr(Statement)
	 * @since 1.0.0
	 */
	public static String tapSystemOutNormalized(
		Statement statement
	) throws Exception {
		return tapSystemOut(statement)
			.replace(lineSeparator(), "\n");
	}

	/**
	 * Executes the statement and restores the system properties after the
	 * statement has been executed. This allows you to set or clear system
	 * properties within the statement without affecting other tests.
	 * <pre>
	 * &#064;Test
	 * public void execute_code_that_manipulates_system_properties(
	 * ) throws Exception {
	 *   System.clearProperty("some property");
	 *   System.setProperty("another property", "value before test");
	 *
	 *   restoreSystemProperties(
	 *     () -&gt; {
	 *       System.setProperty("some property", "some value");
	 *       assertEquals(
	 *         "some value",
	 *         System.getProperty("some property")
	 *       );
	 *
	 *       System.clearProperty("another property");
	 *       assertNull(
	 *         System.getProperty("another property")
	 *       );
	 *     }
	 *   );
	 *
	 *   //values are restored after test
	 *   assertNull(
	 *     System.getProperty("some property")
	 *   );
	 *   assertEquals(
	 *     "value before test",
	 *     System.getProperty("another property")
	 *   );
	 * }
	 * </pre>
	 * @param statement an arbitrary piece of code.
	 * @throws Exception any exception thrown by the statement.
	 * @since 1.0.0
	 */
	public static void restoreSystemProperties(
		Statement statement
	) throws Exception {
		Properties originalProperties = getProperties();
		setProperties(copyOf(originalProperties));
		try {
			statement.execute();
		} finally {
			setProperties(originalProperties);
		}
	}

	private static Properties copyOf(Properties source) {
		Properties copy = new Properties();
		copy.putAll(source);
		return copy;
	}

    /**
     * Executes the statement with the provided security manager.
     * <pre>
     * &#064;Test
     * public void execute_code_with_specific_SecurityManager() {
     *   SecurityManager securityManager = new ASecurityManager();
     *   withSecurityManager(
     *     securityManager,
     *     () -&gt; {
     *       assertSame(securityManager, System.getSecurityManager());
     *     }
     *   );
     * }
     * </pre>
     * The specified security manager is only present during the test.
     * @param securityManager the security manager that is used while the
     *                        statement is executed.
     * @param statement an arbitrary piece of code.
     * @throws Exception any exception thrown by the statement.
     * @since 1.0.0
     */
    public static void withSecurityManager(
        SecurityManager securityManager,
        Statement statement
    ) throws Exception {
        SecurityManager originalSecurityManager = getSecurityManager();
        setSecurityManager(securityManager);
        try {
            statement.execute();
        } finally {
            setSecurityManager(originalSecurityManager);
        }
    }

	private static void executeWithSystemErrReplacement(
		OutputStream replacementForErr,
		Statement statement
	) throws Exception {
		PrintStream originalStream = err;
		try {
			setErr(wrap(replacementForErr));
			statement.execute();
		} finally {
			setErr(originalStream);
		}
	}

	private static void executeWithSystemOutReplacement(
		OutputStream replacementForOut,
		Statement statement
	) throws Exception {
		PrintStream originalStream = out;
		try {
			setOut(wrap(replacementForOut));
			statement.execute();
		} finally {
			setOut(originalStream);
		}
	}

	private static PrintStream wrap(
		OutputStream outputStream
	) throws UnsupportedEncodingException {
		return new PrintStream(
			outputStream,
			AUTO_FLUSH,
			DEFAULT_ENCODING
		);
	}

	private static class DisallowWriteStream extends OutputStream {
		@Override
		public void write(int b) {
			throw new AssertionError(
				"Tried to write '"
					+ (char) b
					+ "' although this is not allowed."
			);
		}
	}

	private static class NoopStream extends OutputStream {
		@Override
		public void write(
			int b
		) {
		}
	}

	private static class TapStream extends OutputStream {
		final ByteArrayOutputStream text = new ByteArrayOutputStream();

		@Override
		public void write(
			int b
		) {
			text.write(b);
		}

		String textThatWasWritten() {
			return text.toString();
		}
	}
}
